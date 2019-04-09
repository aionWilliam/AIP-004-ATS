package AionTokenStandard;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.userlib.AionList;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;

import java.math.BigInteger;

public class AionTokenStandardContract {

    /** ==================================== ATS Contract State ==================================== **/
    private static String tokenName;
    private static String tokenSymbol;
    private static BigInteger tokenTotalSupply;
    private static int tokenGranularity;
    private static Address owner;
    private static Address ATSContractAddress;
    private static Address AionInterfaceRegistryAddress;
    private static Address zeroAddress = new Address("00000000000000000000000000000000".getBytes()); // todo: used to burn tokens
    private static AionMap<Address, TokenHolderInformation> tokenHolderInformation;

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
        if (tokenHolderInformation.containsKey(tokenHolder)) {
            return tokenHolderInformation.get(tokenHolder).balance.toByteArray();
        }
        return BigInteger.ZERO.toByteArray();
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

        if (tokenHolderInformation.containsKey(caller)) {
            if (!tokenHolderInformation.get(caller).operators.contains(operator)) {
                tokenHolderInformation.get(caller).operators.add(operator);
            }
        } else {
            AionList<Address> newOperatorList = new AionList<>();
            newOperatorList.add(operator);
            tokenHolderInformation.put(caller, new AionTokenStandardContract.TokenHolderInformation(BigInteger.ZERO, newOperatorList));
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
        Blockchain.require(caller != operator); // cannot revoke oneself from being an operator

        if (tokenHolderInformation.containsKey(caller)) {
            if (tokenHolderInformation.get(caller).operators.contains(operator)) {
                tokenHolderInformation.get(caller).operators.remove(operator);
                ATSContractEvents.emitRevokedOperatorEvent(operator, caller);
            }
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
            if (tokenHolderInformation.containsKey(tokenHolder)) {
                return tokenHolderInformation.get(tokenHolder).operators.contains(operator);
            }
            else {
                return false;
            }
        }
    }

    /** ==================================== Token Transfers ==================================== **/

    /**
     * Send the amount of tokens from the caller to the recipient
     *
     * @param to token recipient
     * @param amount number of tokens to send
     * @param data information of the transfer
     */
    @Callable
    public static void send(Address to, byte[] amount, byte[] data) {
        Address caller = Blockchain.getCaller();
        doSend(caller, caller, to, new BigInteger(amount), data, new byte[0]);
    }

    /**
     * Send the amount of token on behalf of the address 'from' to the address 'to'
     *
     * @param from token holder
     * @param to token recipient
     * @param amount number of tokens to send
     * @param data information of the transfer
     * @param operatorData information from the operator
     */
    @Callable
    public static void operatorSend(Address from, Address to, byte[] amount, byte[] data, byte[] operatorData) {
        Address caller = Blockchain.getCaller();

        if (tokenHolderInformation.get(from) == null || tokenHolderInformation.get(from).operators.isEmpty()) {
            Blockchain.require(caller.equals(from)); // if there are no operators, check if caller is from itself
        } else {
            Blockchain.require(tokenHolderInformation.get(from).operators.contains(caller)); // else caller must be an operator of 'from'
        }

        doSend(caller, from, to, new BigInteger(amount), data, operatorData);
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

        if (tokenHolderInformation.get(from) == null || tokenHolderInformation.get(from).operators.isEmpty()) {
            Blockchain.require(caller.equals(from)); // if there are no operators, check if caller is from itself
        } else {
            Blockchain.require(tokenHolderInformation.get(from).operators.contains(caller)); // else caller must be an operator of 'from'
        }

        doBurn(caller, from, new BigInteger(amount), senderData, operatorData);
    }

    /** ==================================== Cross-chain Functionality ==================================== **/

    /**
     *  Returns the total supply of tokens currently in circulation on this chain.
     */
    @Callable
    public static byte[] getLiquidSupply() {
        return tokenTotalSupply.subtract(tokenHolderInformation.get(ATSContractAddress).balance).toByteArray();
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

        // check if we have tokenHolderInformation of 'from'
        if (tokenHolderInformation.get(from) == null) {
            tokenHolderInformation.put(from, new TokenHolderInformation(BigInteger.ZERO, new AionList<>()));
        }

        BigInteger senderOriginalBalance = tokenHolderInformation.get(from).balance;
        Blockchain.require(senderOriginalBalance.compareTo(amount) > -1); // amount must be greater or equal to sender balance
        BigInteger receiverOriginalBalance = tokenHolderInformation.get(to) != null ? tokenHolderInformation.get(to).balance : BigInteger.ZERO;

        TokenHolderInformation newSenderInformation = new TokenHolderInformation(senderOriginalBalance.subtract(amount), tokenHolderInformation.get(from).operators);
        TokenHolderInformation newReceiverInformation;

        // check if we have tokenHolderInformation of 'to'
        if (tokenHolderInformation.get(to) == null) {
            newReceiverInformation = new TokenHolderInformation(receiverOriginalBalance.add(amount), new AionList<>());
        } else {
            newReceiverInformation = new TokenHolderInformation(receiverOriginalBalance.add(amount), tokenHolderInformation.get(to).operators);
        }

        tokenHolderInformation.put(from, newSenderInformation);
        tokenHolderInformation.put(to, newReceiverInformation);

        // call these addresses if they are a contract
        if (isContractAddress(from)) {
            Result result = callTokenHolder(from, "tokensToSend", operator, from, to, amount, data, operatorData);
            Blockchain.require(result != null && result.isSuccess());
        }
        if (isContractAddress(to)) {
            Result result2 = callTokenHolder(to, "tokensReceived", operator, from, to, amount, data, operatorData);
            Blockchain.require(result2 != null && result2.isSuccess());
        }

        ATSContractEvents.emitSentEvent(operator, from, to, amount, data, operatorData);
    }

    /**
     * The internal burn implementation
     */
    private static void doBurn (Address operator, Address from, BigInteger amount, byte[] data, byte[] operatorData) {
        Blockchain.require(satisfyGranularity(amount)); // amount must be a multiple of the set tokenGranularity
        Blockchain.require(amount.signum() > -1); // amount must not be negative, 0 is okay

        BigInteger senderOriginalBalance = tokenHolderInformation.get(from).balance;
        Blockchain.require(senderOriginalBalance.compareTo(amount) > -1);

        TokenHolderInformation newSenderInformation = new TokenHolderInformation(senderOriginalBalance.subtract(amount), tokenHolderInformation.get(from).operators);

        tokenHolderInformation.put(from, newSenderInformation);
        tokenTotalSupply = tokenTotalSupply.subtract(amount);

        // call the sender if its a contract
        if (isContractAddress(from)) {
            Result result = callTokenHolder(from, "tokensToSend", operator, from, zeroAddress, amount, data, operatorData);
            Blockchain.require(result != null && result.isSuccess());
        }

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
    private static boolean isContractAddress(Address address) {
        return true;
    }

    /**
     * Initializing the total supply by giving all the tokens to the contract creator.
     */
    private static void initializeTotalSupply(BigInteger totalSupply) {
        tokenHolderInformation.put(owner, new TokenHolderInformation(totalSupply, new AionList<>()));
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
        tokenHolderInformation = new AionMap<>();
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

    private static class TokenHolderInformation {
        BigInteger balance;
        AionList<Address> operators;

        private TokenHolderInformation(BigInteger balance, AionList<Address> operators) {
            this.balance = balance;
            this.operators = operators;
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
    }
}
