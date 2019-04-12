package AionTokenStandard;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.userlib.AionBuffer;
import org.aion.avm.userlib.AionList;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;

import java.math.BigInteger;
import java.util.Arrays;

public class AionTokenStandardContract {

    /** ==================================== ATS Contract State ==================================== **/
    private static String tokenName;
    private static String tokenSymbol;
    private static BigInteger tokenTotalSupply;
    private static int tokenGranularity;
    private static Address owner;
    private static Address ATSContractAddress;
    private static Address AionInterfaceRegistryAddress;
    private static Address zeroAddress = new Address("00000000000000000000000000000000".getBytes());

    private static final String InterfaceName = "AIP004Token";

    /** ==================================== Basic Token Functionality ==================================== **/

    /**
     * Returns the tokenName of the token.
     */
    @Callable
    public static String getName() {
        return tokenName;
    }

    /**
     * Returns the tokenSymbol of the token.
     */
    @Callable
    public static String getSymbol() {
        return tokenSymbol;
    }

    /**
     * Returns the total number of minted tokens across all chains.
     */
    @Callable
    public static byte[] getTotalSupply() {
        return tokenTotalSupply.toByteArray();
    }

    /**
     * Get the smallest part of the token that's not divisible.
     */
    @Callable
    public static int getGranularity() {
        return tokenGranularity;
    }

    /**
     * Get the balance of the account with address tokenHolder on the Home chain.
     *
     * @param tokenHolder Address for which the balance is returned
     * @return Amount of token held by tokenHolder in the token contract.
     */
    @Callable
    public static byte[] balanceOf(Address tokenHolder) {
        byte[] data = Blockchain.getStorage(tokenHolder.unwrap());
        if (data == null) {
            return BigInteger.ZERO.toByteArray();
        }

        return TokenHolderInformation.decodeBalance(data).toByteArray();
    }

    /** ==================================== ERC-777 Operator Functionality ==================================== **/

    /**
     * Set a third party operator address as an operator of caller to send, burn or freeze tokens on its behalf.
     *
     * @param operator Address to set as a operator of caller.
     */
    @Callable
    public static void authorizeOperator(Address operator) {
        Address caller = Blockchain.getCaller();
        Blockchain.require(caller != operator); // there is no point setting oneself as operator
        byte[] callerBytes = caller.unwrap();

        byte[] data = Blockchain.getStorage(callerBytes);

        if (data == null) { // there has been no information stored for this address (caller)
            AionList<Address> operators = new AionList<>();
            operators.add(operator);
            Blockchain.putStorage(callerBytes,TokenHolderInformation.encode(BigInteger.ZERO, operators));
        } else {
            BigInteger balance = TokenHolderInformation.decodeBalance(data);
            AionList<Address> operators = TokenHolderInformation.decodeOperators(data);
            Blockchain.putStorage(callerBytes, TokenHolderInformation.encode(balance, operators));
        }

        ATSContractEvents.emitAuthorizedOperatorEvent(operator, caller);
    }

    /**
     * Remove the right of the operator address from being an operator of caller.
     *
     * @param operator Address to revoke as an operator for caller
     */
    @Callable
    public static void revokeOperator(Address operator) {
        Address caller = Blockchain.getCaller();
        Blockchain.require(caller != operator); // there is no point setting oneself as operator
        byte[] callerBytes = caller.unwrap();

        byte[] data = Blockchain.getStorage(callerBytes);
        if (data != null) {
            AionList<Address> operators = TokenHolderInformation.decodeOperators(data);
            operators.remove(operator);
            BigInteger balance = TokenHolderInformation.decodeBalance(data);
            Blockchain.putStorage(callerBytes, TokenHolderInformation.encode(balance, operators));
        }
    }

    /**
     * Indicates whether the operator address is an operator of the tokenHolder address.
     *
     * @param operator Address which may be an operator of tokenHolder.
     * @param tokenHolder Address of a token holder which may have the operator address as an operator.
     * @return true if operator is an operator of tokenHolder and false otherwise.
     */
    @Callable
    public static boolean isOperatorFor(Address operator, Address tokenHolder) {
        if (operator.equals(tokenHolder)) {
            return true;
        } else {
            byte[] data = Blockchain.getStorage(tokenHolder.unwrap());
            if (data == null) {
                return false;
            }
            AionList<Address> operators = TokenHolderInformation.decodeOperators(data);
            return operators.contains(operator);
        }
    }

    /** ==================================== Token Transfers ==================================== **/

    /**
     * Send the amount of tokens from the caller to the recipient
     *
     * @param to token recipient
     * @param amount number of tokens to send
     * @param senderData information of the transfer
     */
    @Callable
    public static void send(Address to, byte[] amount, byte[] senderData) {
        Address caller = Blockchain.getCaller();
        doSend(caller, caller, to, new BigInteger(amount), senderData, new byte[0]);
    }

    /**
     * Send the amount of token on behalf of the address 'from' to the address 'to'
     *
     * @param from token holder
     * @param to token recipient
     * @param amount number of tokens to send
     * @param senderData information of the transfer
     * @param operatorData information from the operator
     */
    @Callable
    public static void operatorSend(Address from, Address to, byte[] amount, byte[] senderData, byte[] operatorData) {
        Address caller = Blockchain.getCaller();

        // if 'caller' equals 'from', do send
        if (caller.equals(from)) {
            doSend(caller, from, to, new BigInteger(amount), senderData, operatorData);
        }

        // else check for operator
        byte[] fromData = Blockchain.getStorage(from.unwrap());
        Blockchain.require(fromData != null);

        AionList<Address> operators = TokenHolderInformation.decodeOperators(fromData);
        if (operators.contains(caller)) {
            doSend(caller, from, to, new BigInteger(amount), senderData, operatorData);
        }
    }

    /**
     * Burn the amount of tokens from the caller address
     *
     * @param amount number of tokens to send
     * @param senderData information from the sender
     */
    @Callable
    public static void burn(byte[] amount, byte[] senderData) {
        Address caller = Blockchain.getCaller();
        doBurn(caller, caller, new BigInteger(amount), senderData, new byte[0]);
    }

    /**
     * Burn the amount of token on behalf of the address 'from'
     *
     * @param from token holder
     * @param amount number of tokens to send
     * @param senderData information from the sender
     * @param operatorData information from the operator
     */
    @Callable
    public static void operatorBurn(Address from, byte[] amount, byte[] senderData, byte[] operatorData) {
        Address caller = Blockchain.getCaller();

        // if 'caller' equals 'from', do send
        if (caller.equals(from)) {
            doBurn(caller, from, new BigInteger(amount), senderData, operatorData);
        }

        // else check for operator
        byte[] fromData = Blockchain.getStorage(from.unwrap());
        Blockchain.require(fromData != null);

        AionList<Address> operators = TokenHolderInformation.decodeOperators(fromData);
        if (operators.contains(caller)) {
            doBurn(caller, from, new BigInteger(amount), senderData, operatorData);
        }
    }

    /** ==================================== Cross-chain Functionality ==================================== **/

    /**
     *  Returns the total supply of tokens currently in circulation on this chain.
     */
    @Callable
    public static byte[] getLiquidSupply() {
        return tokenTotalSupply.subtract(TokenHolderInformation.decodeBalance(Blockchain.getStorage(ATSContractAddress.unwrap()))).toByteArray();
    }

    @Callable
    public static void thaw(Address localRecipient, byte[] amount, byte[] bridgeId, byte[] bridgeData, byte[] removeSender, byte[] remoteData) {

    }

    @Callable
    public static void freeze(byte[] remoteRecipient, byte[] amount, byte[] bridgeId, byte[] localData) {

    }

    @Callable
    public static void operatorFreeze(Address localSender, byte[] remoteRecipient, byte[] amount, byte[] bridgeId, byte[] localData) {

    }

    /** ==================================== Inner methods ==================================== **/

    /**
     * Check if the number is a multiple of the set tokenGranularity.
     */
    private static boolean satisfyGranularity(BigInteger number) {
        return  (number.mod(BigInteger.valueOf(tokenGranularity)).equals(BigInteger.ZERO));
    }

    /**
     * The internal send implementation
     */
    private static void doSend(Address operator, Address from, Address to, BigInteger amount, byte[] data, byte[] operatorData) {
        Blockchain.require(satisfyGranularity(amount)); // amount must be a multiple of the set tokenGranularity
        Blockchain.require(amount.signum() > -1); // amount must not be negative, 0 is okay
        Blockchain.require(!to.equals(zeroAddress)); // forbid sending to zero address (burning)
        Blockchain.require(!to.equals(ATSContractAddress)); // forbid sending to ATS contract itself

        // check sender info
        BigInteger senderOriginalBalance;
        AionList<Address> senderOriginalOperators;
        byte[] fromData = Blockchain.getStorage(from.unwrap());
        if (fromData == null) {
            senderOriginalBalance = BigInteger.ZERO;
            senderOriginalOperators = new AionList<>();
        } else {
            senderOriginalBalance = TokenHolderInformation.decodeBalance(fromData);
            senderOriginalOperators = TokenHolderInformation.decodeOperators(fromData);
        }

        // check receiver info
        BigInteger receiverOriginalBalance;
        AionList<Address> receiverOriginalOperators;
        byte[] toData = Blockchain.getStorage(to.unwrap());
        if (toData == null) {
            receiverOriginalBalance = BigInteger.ZERO;
            receiverOriginalOperators = new AionList<>();
        } else {
            receiverOriginalBalance = TokenHolderInformation.decodeBalance(toData);
            receiverOriginalOperators = TokenHolderInformation.decodeOperators(toData);
        }

        // check transfer requirements
        Blockchain.require(senderOriginalBalance.compareTo(amount) > -1); // amount must be greater or equal to sender balance

        // call these addresses if they are a contract
        if (isRegularAddress(from)) {
            Result result = callTokenHolder(from, "tokensToSend", operator, from, to, amount, data, operatorData);
            Blockchain.require(result != null && result.isSuccess());
        }
        if (isRegularAddress(to)) {
            Result result2 = callTokenHolder(to, "tokensReceived", operator, from, to, amount, data, operatorData);
            Blockchain.require(result2 != null && result2.isSuccess());
        }

        // do the transfer
        BigInteger senderAfterBalance = senderOriginalBalance.subtract(amount);
        Blockchain.putStorage(from.unwrap(), TokenHolderInformation.encode(senderAfterBalance, senderOriginalOperators));

        BigInteger receiverAfterBalance = receiverOriginalBalance.add(amount);
        Blockchain.putStorage(to.unwrap(), TokenHolderInformation.encode(receiverAfterBalance, receiverOriginalOperators));

        // emit sent event
        ATSContractEvents.emitSentEvent(operator, from, to, amount, data, operatorData);
    }

    /**
     * The internal burn implementation
     */
    private static void doBurn (Address operator, Address from, BigInteger amount, byte[] data, byte[] operatorData) {
        Blockchain.require(satisfyGranularity(amount)); // amount must be a multiple of the set tokenGranularity
        Blockchain.require(amount.signum() > -1); // amount must not be negative, 0 is okay

        // check sender info
        BigInteger senderOriginalBalance;
        AionList<Address> senderOriginalOperators;
        byte[] fromData = Blockchain.getStorage(from.unwrap());
        if (fromData == null) {
            senderOriginalBalance = BigInteger.ZERO;
            senderOriginalOperators = new AionList<>();
        } else {
            senderOriginalBalance = TokenHolderInformation.decodeBalance(fromData);
            senderOriginalOperators = TokenHolderInformation.decodeOperators(fromData);
        }

        // check transfer requirements
        Blockchain.require(senderOriginalBalance.compareTo(amount) > -1); // amount must be greater or equal to sender balance

        // call the sender if its a contract
        if (isRegularAddress(from)) {
            Result result = callTokenHolder(from, "tokensToSend", operator, from, zeroAddress, amount, data, operatorData);
            Blockchain.require(result != null && result.isSuccess());
        }

        // do the transfer
        BigInteger senderAfterBalance = senderOriginalBalance.subtract(amount);
        Blockchain.putStorage(from.unwrap(), TokenHolderInformation.encode(senderAfterBalance, senderOriginalOperators));

        tokenTotalSupply = tokenTotalSupply.subtract(amount);

        // emit burned event
        ATSContractEvents.emitBurnedEvent(operator, from, amount, data, operatorData);
    }

    /**
     * Setup arguments and calls token holder informing the token transfer
     */
    private static Result callTokenHolder(Address contractToCall, String methodName, Address operator, Address from, Address to, BigInteger amount, byte[] data, byte[] operatorData) {
        byte[][] arguments = new byte[7][];
        arguments[0] = ABIEncoder.encodeOneString(methodName);
        arguments[1] = ABIEncoder.encodeOneAddress(operator);
        arguments[2] = ABIEncoder.encodeOneAddress(from);
        arguments[3] = ABIEncoder.encodeOneAddress(to);
        arguments[4] = ABIEncoder.encodeOneByteArray(amount.toByteArray());
        arguments[5] = ABIEncoder.encodeOneByteArray(data);
        arguments[6] = ABIEncoder.encodeOneByteArray(operatorData);

        return Blockchain.call(contractToCall, BigInteger.ZERO, ByteArrayHelpers.concatenateMultiple(arguments), 10_000_000);
    }

    /**
     * Return true if the given address is a contract, false otherwise.
     * todo: we only want to call functions of TokenHolderInterface if the address is a contract, need a way to decide this.
     * todo: need to change this, but for now we are using contracts that implements TokenHolderInterface for testing
     */
    private static boolean isRegularAddress(Address address) {
        return false; // set to true to test  along tokenHolder contract
    }

    /**
     * Initializing the total supply by giving all the tokens to the contract creator.
     */
    private static void initializeTotalSupply(BigInteger totalSupply) {
        Blockchain.putStorage(owner.unwrap(), TokenHolderInformation.encode(totalSupply, new AionList<>()));
        //ATSContractEvents.emitTokenCreatedEvent(totalSupply, owner);
    }

    /**
     * Initialization code executed once at the Dapp deployment. Expect 4 arguments:
     *  - Name of the token (String)
     *  - Symbol of the token (String)
     *  - Granularity of the token (int)
     *  - Total supply of the token (byte[] representation)
     */
    static {
        ABIDecoder decoder = new ABIDecoder(Blockchain.getData());
        tokenName = decoder.decodeOneString();
        tokenSymbol = decoder.decodeOneString();
        tokenGranularity = decoder.decodeOneInteger();
        tokenTotalSupply = new BigInteger(decoder.decodeOneByteArray());
        AionInterfaceRegistryAddress = decoder.decodeOneAddress();
        owner = Blockchain.getCaller();

        Blockchain.require(tokenName.length() > 0);
        Blockchain.require(tokenSymbol.length() > 0);
        Blockchain.require(tokenGranularity >= 1);
        Blockchain.require(tokenTotalSupply.signum() > -1);
        Blockchain.require(AionInterfaceRegistryAddress != null);

        // setup inner data structures
        ATSContractAddress = Blockchain.getAddress();
        initializeTotalSupply(tokenTotalSupply);

        // register the contract in the provided AIR contract
        byte[][] arguments = new byte[4][];
        arguments[0] = ABIEncoder.encodeOneString("setInterfaceImplementer");
        arguments[1] = ABIEncoder.encodeOneAddress(ATSContractAddress);
        arguments[2] = ABIEncoder.encodeOneByteArray(Blockchain.sha256(InterfaceName.getBytes()));
        arguments[3] = ABIEncoder.encodeOneAddress(ATSContractAddress);

        Result result = Blockchain.call(AionInterfaceRegistryAddress, BigInteger.ZERO, ByteArrayHelpers.concatenateMultiple(arguments), 10_000_000);
        Blockchain.require(result != null && result.isSuccess());
    }

    /**
     * Events that this contract emits.
     */

    public static class ATSContractEvents {
        private static String EmitSentEventStringPart1 = "SentEventPart1";
        private static String EmitSentEventStringPart2 = "SentEventPart2";

        private static String EmitBurnedEventStringPart1 = "BurnedEventPart1";
        private static String EmitBurnedEventStringPart2 = "BurnedEventPart2";

        private static String EmitAuthorizedOperatorEventString = "AuthorizedOperatorEvent";
        private static String EmitRevokedOperatorEventString = "RevokedOperatorEvent";
        private static String EmitTokenCreatedEventString = "TokenCreatedEvent";

        public static void emitSentEvent(Address operator, Address from, Address to, BigInteger amount, byte[] senderData, byte[] operatorData) {
            byte[][] data = new byte[3][];
            data[0] = operator.unwrap();
            data[1] = from.unwrap();
            data[2] = to.unwrap();
            Blockchain.log(EmitSentEventStringPart1.getBytes(),
                    "operator".getBytes(),
                    "from".getBytes(),
                    "to".getBytes(),
                    ByteArrayHelpers.concatenateMultiple(data));

            byte[][] data2 = new byte[3][];
            data2[0] = amount.toByteArray();
            data2[1] = senderData;
            data2[2] = operatorData;
            Blockchain.log(EmitSentEventStringPart2.getBytes(),
                    "amount".getBytes(),
                    "senderData".getBytes(),
                    "operatorData".getBytes(),
                    ByteArrayHelpers.concatenateMultiple(data2));
        }

        public static void emitBurnedEvent(Address operator, Address from, BigInteger amount, byte[] senderData, byte[] operatorData) {
            byte[][] data = new byte[2][];
            data[0] = operator.unwrap();
            data[1] = from.unwrap();
            Blockchain.log(EmitBurnedEventStringPart1.getBytes(),
                    "operator".getBytes(),
                    "from".getBytes(),
                    ByteArrayHelpers.concatenateMultiple(data));

            byte[][] data2 = new byte[3][];
            data2[0] = amount.toByteArray();
            data2[1] = senderData;
            data2[2] = operatorData;
            Blockchain.log(EmitBurnedEventStringPart2.getBytes(),
                    "amount".getBytes(),
                    "senderData".getBytes(),
                    "operatorData".getBytes(),
                    ByteArrayHelpers.concatenateMultiple(data2));
        }

        public static void emitAuthorizedOperatorEvent(Address operator, Address tokenHolder) {
            Blockchain.log(EmitAuthorizedOperatorEventString.getBytes(),
                    "operator".getBytes(),
                    "tokenHolder".getBytes(),
                    ByteArrayHelpers.concatenate(operator.unwrap(), tokenHolder.unwrap()));
        }

        public static void emitRevokedOperatorEvent(Address operator, Address tokenHolder) {
            Blockchain.log(EmitRevokedOperatorEventString.getBytes(),
                    "operator".getBytes(),
                    "tokenHolder".getBytes(),
                    ByteArrayHelpers.concatenate(operator.unwrap(), tokenHolder.unwrap()));
        }

        public static void emitTokenCreatedEvent(Address contractOwner, BigInteger totalSupply){
            Blockchain.log(EmitTokenCreatedEventString.getBytes(),
                    "owner".getBytes(),
                    "totalSupply".getBytes(),
                    ByteArrayHelpers.concatenate(contractOwner.unwrap(), totalSupply.toByteArray()));
        }
    }

    /**
     * When storing this object in the key-value store, we will use the following encoding format
     * - byte[0:31]: token balance
     * - byte[32:64]: number of operators
     * - every 32 bytes after: an operator address
     *
     * The first two fields of byte[] will be filled to 32 bytes length in order to store these values in a consistent
     * manner, making decoding very simple.
     */
    private static class TokenHolderInformation {
        private static final int TOKEN_BALANCE_LENGTH = 32;
        private static final int OPERATOR_COUNT_LENGTH = 32;

        private static byte[] encode(BigInteger balance, AionList<Address> operators) {
            int numberOfOperators = operators.size();
            byte[] balanceBytes = ByteArrayHelpers.fillLeadingZeros(balance.toByteArray());
            byte[] operatorsCountBytes = ByteArrayHelpers.fillLeadingZeros(BigInteger.valueOf(numberOfOperators).toByteArray());

            AionBuffer buffer = AionBuffer.allocate(balanceBytes.length  + operatorsCountBytes.length + numberOfOperators * Address.LENGTH);

            buffer.put(balanceBytes).put(operatorsCountBytes);

            for (Address operator: operators) {
                buffer.put(operator.unwrap());
            }

            return buffer.getArray();
        }

        private static BigInteger decodeBalance(byte[] data) {
            return new BigInteger(Arrays.copyOfRange(data, 0, TOKEN_BALANCE_LENGTH));
        }

        private static AionList<Address> decodeOperators(byte[] data) {
            AionList<Address> operators = new AionList<>();
            BigInteger numberOfOperator = new BigInteger(Arrays.copyOfRange(data, TOKEN_BALANCE_LENGTH, TOKEN_BALANCE_LENGTH + OPERATOR_COUNT_LENGTH));

            if (numberOfOperator.signum() >= 0) {
                for (int i = (TOKEN_BALANCE_LENGTH + OPERATOR_COUNT_LENGTH); i < (TOKEN_BALANCE_LENGTH + OPERATOR_COUNT_LENGTH) + numberOfOperator.intValue() * Address.LENGTH; i = i + Address.LENGTH) {
                    operators.add(new Address(Arrays.copyOfRange(data, i, i + Address.LENGTH)));
                }
            }
            return operators;
        }
    }

    /**
     * Helper class for manipulating byte arrays.
     */

    public static class ByteArrayHelpers {
        public static byte[] concatenate(byte[] one, byte[] two) {
            byte[] result = new byte[one.length + two.length];
            System.arraycopy(one, 0, result, 0, one.length);
            System.arraycopy(two, 0, result, one.length, two.length);
            return result;
        }

        public static byte[] concatenateMultiple(byte[][] bytes) {
            byte[] result = new byte[0];
            for (byte[] bytes1: bytes) {
                result = concatenate(result, bytes1);
            }
            return result;
        }
        public static byte[] fillLeadingZeros(byte[] byteArray) {
            if (byteArray.length >= 32) {
                return byteArray;
            }

            byte[] zeroBytes = new byte[32 - byteArray.length];
            for (int i = 0; i < zeroBytes.length; i++) {
                zeroBytes[i] = 0x0;
            }

            return AionBuffer.allocate(32).put(zeroBytes).put(byteArray).getArray();
        }
    }
}
