package HelperContracts;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.tooling.abi.Callable;

import java.util.Arrays;

public class Interface1ImplementerContract {

    private static String interfaceImplemented;
    private static Address addressSupport;

    /**
     * returns whether we support the given interface on the given target
     */
    @Callable
    public static boolean isImplementerFor(Address target, byte[] interfaceHash) {
        byte[] hash = Blockchain.sha256(interfaceImplemented.getBytes());
        if (Arrays.equals(hash, interfaceHash)) {
            return true;
        }
        return false;
    }

    private static Interface1ImplementerContract interface1ImplementerContract;

    static {
        interface1ImplementerContract = new Interface1ImplementerContract();
        interfaceImplemented = "Interface1";
        addressSupport = Blockchain.getAddress();
    }

}
