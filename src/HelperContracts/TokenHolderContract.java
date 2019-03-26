package HelperContracts;

import org.aion.avm.api.ABIDecoder;
import org.aion.avm.api.Address;
import org.aion.avm.api.BlockchainRuntime;

public class TokenHolderContract {

    private static String name;

    public static void tokensReceived(Address operator, Address from, Address to, long amount, byte[] userData, byte[] operatorData) {
        BlockchainRuntime.println("TOKENS RECEIVED: " + name
                + " [operator: " + operator
                + " from: " + from
                + " to: " + to
                + " amount: " + amount
                + " userData: " + userData
                + " operatorData: " + operatorData
                + " ] \n");
    }

    public static void tokensToSend(Address operator, Address from, Address to, long amount, byte[] userData, byte[] operatorData) {
        BlockchainRuntime.println("TOKENS TO SEND: " + name
                + " [operator: " + operator
                + " from: " + from
                + " to: " + to
                + " amount: " + amount
                + " userData: " + userData
                + " operatorData: " + operatorData
                + " ] \n");
    }

    private static TokenHolderContract tokenHolderContract;

    static {
        Object[] arguments = ABIDecoder.decodeArguments(BlockchainRuntime.getData());
        BlockchainRuntime.require(arguments != null);
        BlockchainRuntime.require(arguments.length == 1);

        tokenHolderContract = new TokenHolderContract();
        name = new String((char[]) arguments[0]);
    }

    /**
     * Entry point at a transaction call.
     */
    public static byte[] main() {
        return ABIDecoder.decodeAndRunWithClass(TokenHolderContract.class, BlockchainRuntime.getData());
    }

}
