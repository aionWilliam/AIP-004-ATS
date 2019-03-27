package HelperContracts;

import org.aion.avm.api.Address;
import org.aion.avm.api.BlockchainRuntime;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.userlib.abi.ABIDecoder;

public class TokenHolderContract {

    private static String name;

    @Callable
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

    @Callable
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

}
