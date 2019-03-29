package AionTokenStandard;

import org.aion.avm.api.*;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.userlib.AionList;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.AionBuffer;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;

import java.math.BigInteger;

public class AionTokenStandardContract {

    /** ==================================== ATS Contract State ==================================== **/
    private static String tokenName;
    private static String tokenSymbol;
    private static long tokenTotalSupply;
    private static int tokenGranularity;
    private static Address owner;
    private static Address ATSContractAddress;
    private static Address AionInterfaceRegistryAddress;

    private static AionMap<Address, Long> ledger; // <token holder, balance>
    private static AionMap<Address, AionList<Address>> operators; // <token holder, list of operators>

    private static final String InterfaceName = "AIP004ATS";

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
    public static long getTotalSupply() {
        return tokenTotalSupply;
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
    public static long balanceOf(Address tokenHolder) {
        return ledger.getOrDefault(tokenHolder, 0L);
    }


    /** ==================================== ERC-777 Operator Functionality ==================================== **/

    /**
     * Set a third party operator address as an operator of caller to send, burn or freeze tokens on its behalf.
     *
     * @param operator Address to set as a operator of caller.
     */
    @Callable
    public static void authorizeOperator(Address operator) {
        Address caller = BlockchainRuntime.getCaller();
        BlockchainRuntime.require(caller != operator);

        if (operators.containsKey(caller)) {
            if (!operators.get(caller).contains(operator)) {
                operators.get(caller).add(operator);
            }
        } else {
            AionList<Address> newOperatorList = new AionList<>();
            newOperatorList.add(operator);
            operators.put(caller,newOperatorList);
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
        BlockchainRuntime.require(BlockchainRuntime.getCaller() != operator);
        Address caller = BlockchainRuntime.getCaller();

        if (operators.containsKey(caller)) {
            if (operators.get(caller).contains(operator)) {
                operators.get(caller).remove(operator);
            }
        }
        ATSContractEvents.emitRevokedOperatorEvent(operator, caller);
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
        if (operators.containsKey(tokenHolder)) {
            return operators.get(tokenHolder).contains(operator);
        }
        return false;
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
    public static void send(Address to, long amount, byte[] data) {
        Address caller = BlockchainRuntime.getCaller();
        doSend(caller, caller, to, amount, data, new byte[0]);
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
    public static void operatorSend(Address from, Address to, long amount, byte[] data, byte[] operatorData) {
        Address caller = BlockchainRuntime.getCaller();

        if (operators.get(from) == null || operators.get(from).isEmpty()) {
            BlockchainRuntime.require(caller.equals(from)); // if there are no operators, check if caller is from itself
        } else {
            BlockchainRuntime.require(operators.get(from).contains(caller)); // else caller must be an operator of 'from'
        }

        doSend(caller, from, to, amount, data, operatorData);
    }

    /**
     * Burn the amount of tokens from the caller address
     *
     * @param amount number of tokens to send
     * @param senderData information from the sender
     */
    @Callable
    public static void burn(long amount, byte[] senderData) {
        Address caller = BlockchainRuntime.getCaller();
        doBurn(caller, caller, amount, senderData, new byte[0]);
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
    public static void operatorBurn(Address from, long amount, byte[] senderData, byte[] operatorData) {
        Address caller = BlockchainRuntime.getCaller();

        if (operators.get(from) == null || operators.get(from).isEmpty()) {
            BlockchainRuntime.require(caller.equals(from)); // if there are no operators, check if caller is from itself
        } else {
            BlockchainRuntime.require(operators.get(from).contains(caller)); // else caller must be an operator of 'from'
        }

        doBurn(caller, from, amount, senderData, operatorData);
    }

    /** ==================================== Cross-chain Functionality ==================================== **/

    /**
     *  Returns the total supply of tokens currently in circulation on this chain.
     */
    @Callable
    public static long getLiquidSupply() {
        return tokenTotalSupply - ledger.get(ATSContractAddress);
    }

    @Callable
    public static void thaw(Address localRecipient, long amount, byte[] bridgeId, byte[] bridgeData, byte[] removeSender, byte[] remoteData) {

    }

    @Callable
    public static void freeze(byte[] remoteRecipient, long amount, byte[] bridgeId, byte[] localData) {

    }

    @Callable
    public static void operatorFreeze(Address localSender, byte[] remoteRecipient, long amount, byte[] bridgeId, byte[] localData) {

    }

    /** ==================================== Inner methods ==================================== **/

    /**
     * Check if the number is a multiple of the set tokenGranularity.
     */
    private static boolean satisfyGranularity(long number) {
        return  (number % tokenGranularity) == 0 ;
    }

    /**
     * The internal send implementation
     */
    private static void doSend(Address operator, Address from, Address to, long amount, byte[] data, byte[] operatorData) {
        BlockchainRuntime.require(satisfyGranularity(amount)); // amount must be a multiple of the set tokenGranularity
        BlockchainRuntime.require(amount >= 0); // amount must not be negative

        long senderOriginalBalance = ledger.get(from);
        BlockchainRuntime.require(senderOriginalBalance >= amount); // amount must be greater or equal to sender balance
        long receiverOriginalBalance = ledger.get(to) != null ? ledger.get(to) : 0;

        ledger.put(from, senderOriginalBalance - amount);
        ledger.put(to, receiverOriginalBalance + amount);

        ATSContractEvents.emitSentEvent(operator, from, to, amount, data, operatorData);
    }

    /**
     * The internal burn implementation
     */
    private static void doBurn (Address operator, Address from, long amount, byte[] senderData, byte[] operatorData) {
        BlockchainRuntime.require(satisfyGranularity(amount)); // amount must be a multiple of the set tokenGranularity
        BlockchainRuntime.require(amount >= 0); // amount must not be negative

        long senderOriginalBalance = ledger.get(from);
        BlockchainRuntime.require(senderOriginalBalance >= amount); // amount must be greater or equal to sender balance

        ledger.put(from, senderOriginalBalance - amount);
        tokenTotalSupply = tokenTotalSupply - amount;

        ATSContractEvents.emitBurnedEvent(operator, from, amount, senderData, operatorData);
    }

    /**
     * Initializing the total supply by giving all the tokens to the contract creator.
     */
    private static void initializeTotalSupply(long totalSupply) {
        ledger.put(owner, totalSupply);
    }

    /**
     * Initialization code executed once at the Dapp deployment. Expect 4 arguments:
     *  - Name of the token
     *  - Symbol of the token
     *  - Granularity of the token
     *  - Total supply of the token
     *  - AIR Address
     */
    static {
        Object[] arguments = ABIDecoder.decodeDeploymentArguments(BlockchainRuntime.getData());
        BlockchainRuntime.require(arguments != null);
        BlockchainRuntime.require(arguments.length == 5);

        // parse inputs
        tokenName = (String) arguments[0];
        tokenSymbol = (String) arguments[1];
        tokenGranularity = (int) arguments[2];
        tokenTotalSupply = (long) arguments[3];
        AionInterfaceRegistryAddress = (Address) arguments[4];
        owner = BlockchainRuntime.getCaller();

        // check for inputs todo: decision
        BlockchainRuntime.require(tokenName.length() > 0);
        BlockchainRuntime.require(tokenSymbol.length() > 0);
        BlockchainRuntime.require(tokenGranularity >= 1);
        BlockchainRuntime.require(tokenTotalSupply >= 0);
        BlockchainRuntime.require(AionInterfaceRegistryAddress != null);

        // setup inner data structures
        ledger = new AionMap<>();
        operators = new AionMap<>();
        ATSContractAddress = BlockchainRuntime.getAddress();
        initializeTotalSupply(tokenTotalSupply);

        // register the contract in the provided AIR contract
        BlockchainRuntime.call(AionInterfaceRegistryAddress, BigInteger.ZERO,
                ABIEncoder.encodeMethodArguments("setInterfaceImplementer",
                        ATSContractAddress, BlockchainRuntime.sha256(InterfaceName.getBytes()), ATSContractAddress), 10_000_000);
    }

    /**
     * Events that this contract emits.
     */

    public static class ATSContractEvents {
        private static String EmitSentEventString = "SentEvent";
        private static String EmitThawedEventString = "ThawedEvent";
        private static String EmitFrozeEventString = "FrozeEvent";
        private static String EmitMintedEventString = "MintedEvent";
        private static String EmitBurnedEventString = "BurnedEvent";
        private static String EmitAuthorizedOperatorEventString = "AuthorizedOperatorEvent";
        private static String EmitRevokedOperatorEventString = "RevokedOperatorEvent";

        public static void emitSentEvent(Address operator, Address from, Address to, long amount, byte[] senderData, byte[] operatorData) {
            byte[][] data = new byte[6][];
            data[0] = operator.unwrap();
            data[1] = from.unwrap();
            data[2] = to.unwrap();
            data[3] = ByteArrayHelpers.longToBytes(amount);
            data[4] = senderData;
            data[5] = operatorData;

            BlockchainRuntime.log(EmitSentEventString.getBytes(), ByteArrayHelpers.concatenateMultiple(data));
        }

        public static void emitThawedEvent(Address localRecipient, byte[] remoteSender, long amount, byte[] bridgeID, byte[] bridgeData, byte[] remoteBridgeId, byte[] remoteData ) {
            byte[][] data = new byte[7][];
            data[0] = localRecipient.unwrap();
            data[1] = remoteSender;
            data[2] = ByteArrayHelpers.longToBytes(amount);
            data[3] = bridgeID;
            data[4] = bridgeData;
            data[5] = remoteBridgeId;
            data[6] = remoteData;

            BlockchainRuntime.log(EmitThawedEventString.getBytes(), ByteArrayHelpers.concatenateMultiple(data));
        }

        public static void emitFrozeEvent(Address localSender, byte[] remoteRecipient, long amount, byte[] bridgeID, byte[] localData ) {
            byte[][] data = new byte[5][];
            data[0] = localSender.unwrap();
            data[1] = remoteRecipient;
            data[2] = ByteArrayHelpers.longToBytes(amount);
            data[3] = bridgeID;
            data[4] = localData;

            BlockchainRuntime.log(EmitFrozeEventString.getBytes(), ByteArrayHelpers.concatenateMultiple(data));
        }

        public static void emitMintedEvent(Address operator, Address to, long amount, byte[] operatorData) {
            byte[][] data = new byte[4][];
            data[0] = operator.unwrap();
            data[1] = to.unwrap();
            data[2] = ByteArrayHelpers.longToBytes(amount);
            data[3] = operatorData;

            BlockchainRuntime.log(EmitMintedEventString.getBytes(), ByteArrayHelpers.concatenateMultiple(data));
        }

        public static void emitBurnedEvent(Address operator, Address from, long amount, byte[] senderData, byte[] operatorData) {
            byte[][] data = new byte[5][];
            data[0] = operator.unwrap();
            data[1] = from.unwrap();
            data[2] = ByteArrayHelpers.longToBytes(amount);
            data[3] = senderData;
            data[4] = operatorData;

            BlockchainRuntime.log(EmitBurnedEventString.getBytes(), ByteArrayHelpers.concatenateMultiple(data));
        }

        public static void emitAuthorizedOperatorEvent(Address operator, Address tokenHolder) {
            BlockchainRuntime.log(EmitAuthorizedOperatorEventString.getBytes(), ByteArrayHelpers.concatenate(operator.unwrap(), tokenHolder.unwrap()));
        }

        public static void emitRevokedOperatorEvent(Address operator, Address tokenHolder) {
            BlockchainRuntime.log(EmitRevokedOperatorEventString.getBytes(), ByteArrayHelpers.concatenate(operator.unwrap(), tokenHolder.unwrap()));
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

        public static byte[] longToBytes(long x) {
            AionBuffer buffer = AionBuffer.allocate(Long.BYTES);
            buffer.putLong(x);
            return buffer.getArray();
        }
    }
}
