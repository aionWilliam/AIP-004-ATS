package HelperContracts;

import org.aion.avm.api.Address;
import org.aion.avm.api.BlockchainRuntime;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.userlib.abi.ABIDecoder;

import java.math.BigInteger;

public class TokenHolderContract {

    private static String name;

    @Callable
    public static void tokensReceived(Address operator, Address from, Address to, byte[] amount, byte[] userData, byte[] operatorData) {
        BlockchainRuntime.println("TOKENS RECEIVED: " + name
                + " [operator: " + operator
                + " from: " + from
                + " to: " + to
                + " amount: " + new BigInteger(amount)
                + " userData: " + userData
                + " operatorData: " + operatorData
                + " ]");
    }

    @Callable
    public static void tokensToSend(Address operator, Address from, Address to, byte[] amount, byte[] userData, byte[] operatorData) {
        BlockchainRuntime.println("TOKENS TO SEND: " + name
                + " [operator: " + operator
                + " from: " + from
                + " to: " + to
                + " amount: " + new BigInteger(amount)
                + " userData: " + userData
                + " operatorData: " + operatorData
                + " ]");
    }

    private static TokenHolderContract tokenHolderContract;

    static {
        Object[] arguments = ABIDecoder.decodeDeploymentArguments(BlockchainRuntime.getData());
        BlockchainRuntime.require(arguments != null);
        BlockchainRuntime.require(arguments.length == 1);

        tokenHolderContract = new TokenHolderContract();
        name = (String) arguments[0];
    }

}
