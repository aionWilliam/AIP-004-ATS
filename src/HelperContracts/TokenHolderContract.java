package HelperContracts;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.userlib.abi.ABIDecoder;

import java.math.BigInteger;

public class TokenHolderContract {

    private static String name;

    @Callable
    public static void tokensReceived(Address operator, Address from, Address to, byte[] amount, byte[] userData, byte[] operatorData) {
        Blockchain.println("TOKENS RECEIVED: " + name
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
        Blockchain.println("TOKENS TO SEND: " + name
                + " [operator: " + operator
                + " from: " + from
                + " to: " + to
                + " amount: " + new BigInteger(amount)
                + " userData: " + userData
                + " operatorData: " + operatorData
                + " ]");
    }

    static {
        ABIDecoder decoder = new ABIDecoder(Blockchain.getData());
        String arg = decoder.decodeOneString();
        Blockchain.require(arg != null);
        name = arg;
    }
}
