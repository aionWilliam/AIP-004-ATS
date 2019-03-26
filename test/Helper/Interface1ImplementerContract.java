package Helper;

import org.aion.avm.api.ABIDecoder;
import org.aion.avm.api.Address;
import org.aion.avm.api.BlockchainRuntime;
import java.util.Arrays;

public class Interface1ImplementerContract {

    private static String interfaceImplemented;
    private static Address addressSupport;

    /**
     * returns whether we support the given interface on the given target
     */
    public static boolean isImplementerFor(Address target, byte[] interfaceHash) {
        byte[] hash = BlockchainRuntime.sha256(interfaceImplemented.getBytes());
        if (Arrays.equals(hash, interfaceHash)) {
            return true;
        }
        return false;
    }

    private static Interface1ImplementerContract interface1ImplementerContract;

    static {
        interface1ImplementerContract = new Interface1ImplementerContract();
        interfaceImplemented = "Interface1";
        addressSupport = BlockchainRuntime.getAddress();
    }

    /**
     * Entry point at a transaction call.
     */
    public static byte[] main() {
        return ABIDecoder.decodeAndRunWithClass(Interface1ImplementerContract.class, BlockchainRuntime.getData());
    }
}
