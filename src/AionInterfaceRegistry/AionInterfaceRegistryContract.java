package AionInterfaceRegistry;

import org.aion.avm.api.*;
import org.aion.avm.userlib.AionMap;

import java.math.BigInteger;
import java.util.Arrays;

public class AionInterfaceRegistryContract {

    // todo: we really should set restriction on setting manager
    // todo: calling setInterfaceImplementer, maybe allow for both manager and self to set it.
    private static AionMap<Address, AionMap<ByteArrayWrapper, Address>> interfaces; // <contract, <interface hash, implementer>>
    private static AionMap<Address, Address> managers; // <address, its manager>

    /**
     * Called to get the address of the manager which controls the registration of the 'target'.
     * @param target Address supporting an interface.
     * @return the target's manager.
     */
    public static Address getManager(Address target) {
        // by defult the manager of an address is itself
        return managers.getOrDefault(target, target);
    }

    /**
     * Called to set the address of the manager which controls the registration of the 'target'.
     * @param target Address supporting an interface.
     * @param newManager Address of the manager for 'target'.
     */
    public static void setManager(Address target, Address newManager) {
        if (target == newManager) { // if setting self as manager, remove old manager if it is present
            managers.remove(target); // note in solidity impl, they set the manager to 0x0
        } else {
            managers.put(target, newManager);
        }
    }

    /**
     * Called to get the address of the delegate implementing the interfaceHash on behalf of the 'target'.
     *
     * @param target Address supporting an interface.
     * @param interfaceHash sha256 hash of the interface.
     * @return The address of the contract which implements the interface 'interfaceHash' for 'target'
     */
    public static Address getInterfaceImplementer(Address target, byte[] interfaceHash) {
       return interfaces.get(target).get(new ByteArrayWrapper(interfaceHash));
    }

    /**
     * Called to set the address of the delegate which implements the interface 'interfaceHash' on behalf of the 'target'.
     * @param target Address supporting an interface.
     * @param interfaceHash sha256 hash of the interface.
     * @param implementer Address implementing the interface on behalf of 'target'.
     */
    public static void setInterfaceImplementer(Address target, byte[] interfaceHash, Address implementer) {
        Address caller = BlockchainRuntime.getCaller();

        Address manager = getManager(target);
        BlockchainRuntime.require(manager.equals(caller));

        if (!implementer.equals(caller)) {
            // call the implementer to verify it implements the AIRImplementerInterface
            Result callResult = BlockchainRuntime.call(implementer, BigInteger.ZERO, ABIEncoder.encodeMethodArguments("isImplementerFor", target, interfaceHash), 10_000_000);
            byte[] data = callResult.getReturnData(); // todo: check for null and decide what to do
            boolean result = (boolean)ABIDecoder.decodeOneObject(data);
            BlockchainRuntime.require(result);
        }

        AionMap<ByteArrayWrapper, Address> interfacesImplemented;
        if (interfaces.containsKey(target)) {
            interfacesImplemented = interfaces.get(target);
        } else {
            interfacesImplemented = new AionMap<>();
        }
        interfacesImplemented.put(new ByteArrayWrapper(interfaceHash), implementer);
        interfaces.put(target, interfacesImplemented);
    }

    private boolean verifyImplementsInterface() {
        return true; //todo: WIP
        //            Result callResult = BlockchainRuntime.call(implementer, BigInteger.ZERO, ABIEncoder.encodeMethodArguments(AIRImplementerInterface.class.getDeclaredMethods()[0].getName()"isImplementerFor"), 10_000_000);
    }

    /**
     * Called to get the sha256 hash of an interface given its name as a string interfaceName.
     * todo: decide if we still want this inside of the contract
     * @param interfaceName String name of the interface name.
     * @return the sha256 hash of 'interfaceName'.
     */
    private static byte[] interfaceHash(String interfaceName) {
        return BlockchainRuntime.sha256(interfaceName.getBytes());
    }

    private static AionInterfaceRegistryContract aionInterfaceRegistryContract;

    /**
     * Initialization code executed once at the Dapp deployment.
     */
    static {
        aionInterfaceRegistryContract = new AionInterfaceRegistryContract();
        managers = new AionMap<>();
        interfaces = new AionMap<>();
    }


    /**
     * Entry point at a transaction call.
     */
    public static byte[] main() {
        return ABIDecoder.decodeAndRunWithClass(AionInterfaceRegistryContract.class, BlockchainRuntime.getData());
    }

    public static class ByteArrayWrapper {
        private byte[] bytes;

        ByteArrayWrapper(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ByteArrayWrapper)) {
                return false;
            }
            return Arrays.equals(this.bytes, ((ByteArrayWrapper)other).getBytes());
        }

        @Override
        public int hashCode(){
            return 1;
        }

        byte[] getBytes() {
            return this.bytes;
        }
    }
}
