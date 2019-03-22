package AionTokenStandard;

import org.aion.avm.api.ABIDecoder;
import org.aion.avm.api.Address;
import org.aion.avm.api.BlockchainRuntime;
import org.aion.avm.userlib.AionList;
import org.aion.avm.userlib.AionMap;

public class AionTokenStandardContract {

    /** ================== ATS Contract State ================== **/
    private static String tokenName;
    private static String tokenSymbol;
    private static long tokenTotalSupply;
    private static int tokenGranularity;
    private static Address owner;
    private static Address contractAddress;

    private static AionMap<Address, Long> ledger; // <token holder, balance>
    private static AionMap<Address, AionMap<Address, Long>> allowance;  // <token holder, <allowed address, amount>>
    private static AionMap<Address, AionList<Address>> operators; // <token holder, list of operators>

    /**
     * Temporary constructor
     */
    public AionTokenStandardContract(String mName, String mSymbol, int mGranularity, long mTotalSupply, Address mCaller) {
        tokenName = mName;
        tokenSymbol = mSymbol;
        tokenGranularity = mGranularity;
        tokenTotalSupply = mTotalSupply;
        owner = mCaller;
        ledger = new AionMap<>();
        allowance = new AionMap<>();
        operators = new AionMap<>();
        initializeTotalSupply(tokenTotalSupply);
        // todo: set interface delegate ("AIP004Token", this.contractAddress)
    }

    /**
     * Returns the tokenName of the token.
     */
    public String getName() {
        return tokenName;
    }

    /**
     * Returns the tokenSymbol of the token.
     */
    public String getSymbol() {
        return tokenSymbol;
    }

    /**
     * Returns the total number of minted tokens across all chains.
     */
    public long getTotalSupply() {
        return tokenTotalSupply;
    }

    /**
     * Get the smallest part of the token that's not divisible.
     */
    public int getGranularity() {
        return tokenGranularity;
    }

    /**
     * Get the balance of the account with address tokenHolder on the Home chain.
     *
     * @param tokenHolder Address for which the balance is returned
     * @return Amount of token held by tokenHolder in the token contract.
     */
    public static long balanceOf(Address tokenHolder) {
        return ledger.getOrDefault(tokenHolder, 0L);
    }

//    public static long allowance(Address tokenOwner, Address spender) {
//        if (!allowance.containsKey(tokenOwner)) {
//            return 0L;
//        }
//
//        return allowance.get(tokenOwner).getOrDefault(spender, 0L);
//    }

    /**
     * Set a third party operator address as an operator of caller to send, burn or freeze tokens on its behalf.
     *
     * @param operator Address to set as a operator of caller.
     */
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
        // todo: log(AuthorizedOperator)
    }

    /**
     * Remove the right of the operator address from being an operator of caller.
     *
     * @param operator Address to rescind as an operator for caller
     */
    public static void revokeOperator(Address operator) {
        BlockchainRuntime.require(BlockchainRuntime.getCaller() != operator);
        Address caller = BlockchainRuntime.getCaller();

        if (operators.containsKey(caller)) {
            if (operators.get(caller).contains(operator)) {
                operators.get(caller).remove(operator);
            }
        }
        // todo: log(RevokedOperator)

    }

    /**
     * Indicates whether the operator address is an operator of the tokenHolder address.
     *
     * @param operator Address which may be an operator of tokenHolder.
     * @param tokenHolder Address of a token holder which may have the operator address as an operator.
     * @return true if operator is an operator of tokenHolder and false otherwise.
     */
    public static boolean isOperatorFor(Address operator, Address tokenHolder) {
        if (operators.containsKey(tokenHolder)) {
            return operators.get(tokenHolder).contains(operator);
        }
        return false;
    }

    /**
     * Send the amount of tokens from the caller to the recipient
     *
     * @param to token recipient
     * @param amount number of tokens to send
     * @param data information of the transfer
     */
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
    public static void operatorSend(Address from, Address to, long amount, byte[] data, byte[] operatorData) {
        Address caller = BlockchainRuntime.getCaller();
        BlockchainRuntime.require(operators.get(from).contains(caller)); // caller must be an operator of 'from'
        doSend(caller, from, to, amount, data, operatorData);
    }

    /**
     * Burn the amount of tokens from the caller address
     *
     * @param amount number of tokens to send
     */
    public static void burn(long amount) {
        Address caller = BlockchainRuntime.getCaller();
        doBurn(caller, caller, amount, new byte[0]);
    }

    /**
     * Burn the amount of token on behalf of the address 'from'
     *
     * @param from token holder
     * @param amount number of tokens to send
     * @param operatorData information from the operator
     */
    public static void operatorBurn(Address from, long amount, byte[] operatorData) {
        Address caller = BlockchainRuntime.getCaller();
        BlockchainRuntime.require(operators.get(from).contains(caller));
        doBurn(caller, from, amount, operatorData);
    }

    /**
     *  Returns the total supply of tokens currently in circulation on this chain.
     */
    public static long getLiquidSupply() {
        return tokenTotalSupply - ledger.get(contractAddress);
    }

    /**
     * Check if the number is a multiple of the set tokenGranularity.
     */
    private static boolean satisfyGranularity(long number) {
        return  (number / tokenGranularity) == 0 ;
    }

    /**
     * The internal send implementation
     */
    private static void doSend(Address operator, Address from, Address to, long amount, byte[] data, byte[] operatorData) {
        BlockchainRuntime.require(satisfyGranularity(amount)); // amount must be a multiple of the set tokenGranularity

        long senderOriginalBalance = ledger.get(from);
        BlockchainRuntime.require(senderOriginalBalance >= amount); // amount must be greater or equal to sender balance
        long receiverOriginalBalance = ledger.get(to) != null ? ledger.get(to) : 0;

        ledger.put(from, senderOriginalBalance - amount);
        ledger.put(to, receiverOriginalBalance + amount);

        // todo: log(sent), call sender, call recipient
    }

    /**
     * The internal burn implementation
     */
    private static void doBurn (Address operator, Address from, long amount, byte[] operatorData) {
        BlockchainRuntime.require(satisfyGranularity(amount)); // amount must be a multiple of the set tokenGranularity

        long senderOriginalBalance = ledger.get(from);
        BlockchainRuntime.require(senderOriginalBalance >= amount); // amount must be greater or equal to sender balance

        ledger.put(from, senderOriginalBalance - amount);
        tokenTotalSupply = tokenTotalSupply - amount;

        // todo: log(burned), call sender, call recipient
    }

    /**
     * Initializing the total supply by giving all the tokens to the contract creator.
     */
    private static void initializeTotalSupply(long totalSupply) {
        ledger.put(owner, totalSupply);
    }

    private static AionTokenStandardContract aionTokenStandardContract;

    /**
     * Initialization code executed once at the Dapp deployment. Expect 4 arguments:
     *  - Name of the token
     *  - Symbol of the token
     *  - Granularity of the token
     *  - Total supply of the token
     */
    static {
        Object[] arguments = ABIDecoder.decodeArguments(BlockchainRuntime.getData());
        BlockchainRuntime.require(arguments != null);
        BlockchainRuntime.require(arguments.length == 4);

        String name = new String((char[]) arguments[0]);
        String symbol = new String((char[]) arguments[1]);
        int granularity = (int) arguments[2];
        long totalSupply = (long) arguments[3];

        BlockchainRuntime.require(granularity >= 1);

        Address caller = BlockchainRuntime.getCaller();
        aionTokenStandardContract = new AionTokenStandardContract(name, symbol, granularity, totalSupply, caller);
        contractAddress = BlockchainRuntime.getAddress();

        // todo: maybe combine this with constructor
    }

    /**
     * Entry point at a transaction call.
     */
    public static byte[] main() {
        return ABIDecoder.decodeAndRunWithClass(AionTokenStandardContract.class, BlockchainRuntime.getData());
    }
}
