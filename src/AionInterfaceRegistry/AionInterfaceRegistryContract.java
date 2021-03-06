package AionInterfaceRegistry;

import avm.Address;
import avm.Blockchain;
import avm.Result;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;

import java.math.BigInteger;
import java.util.Arrays;

public class AionInterfaceRegistryContract {

    private static AionMap<Address, AionMap<ByteArrayWrapper, Address>> interfaces; // <contract, <interface hash, implementer>>
    private static AionMap<Address, Address> managers; // <address, its manager>

    /**
     * Called to get the address of the manager which controls the registration of the 'target'.
     *
     * @param target Address supporting an interface.
     * @return the target's manager.
     */
    @Callable
    public static Address getManager(Address target) {
        // by defult the manager of an address is itself
        return managers.getOrDefault(target, target);
    }

    /**
     * Called to set the address of the manager which controls the registration of the 'target'. Only
     * the target itself or the current manager can call this.
     *
     * @param target Address supporting an interface.
     * @param newManager Address of the manager for 'target'.
     */
    @Callable
    public static void setManager(Address target, Address newManager) {
        Address caller = Blockchain.getCaller();
        Blockchain.require(caller.equals(target) || caller.equals(getManager(target)));

        if (target == newManager) { // if setting self as manager, remove old manager if it is present
            managers.remove(target); // note in solidity impl, they set the manager to 0x0
        } else {
            managers.put(target, newManager);
        }
        AIRContractEvents.emitManagerChangedEvent(target, newManager);
    }

    /**
     * Called to get the address of the delegate implementing the interfaceHash on behalf of the 'target'.
     *
     * @param target Address supporting an interface.
     * @param interfaceHash sha256 hash of the interface.
     * @return The address of the contract which implements the interface 'interfaceHash' for 'target'
     */
    @Callable
    public static Address getInterfaceImplementer(Address target, byte[] interfaceHash) {
       return interfaces.get(target).get(new ByteArrayWrapper(interfaceHash));
    }

    /**
     * Called to set the address of the delegate which implements the interface 'interfaceHash' on behalf of the 'target'.
     *
     * @param target Address supporting an interface.
     * @param interfaceHash sha256 hash of the interface.
     * @param implementer Address implementing the interface on behalf of 'target'.
     */
    @Callable
    public static void setInterfaceImplementer(Address target, byte[] interfaceHash, Address implementer) {
        Address caller = Blockchain.getCaller();
        Address manager = getManager(target);
        Blockchain.require(manager.equals(caller) || target.equals(caller)); // should only allow manager and target itself to set implementer

        // if the caller is not the implementer, call the implementer to verify it implements the AIRImplementerInterface
        if (!implementer.equals(caller)) {
            Result callResult = checkImplementer(implementer, "isImplementerFor", target, interfaceHash);
            Blockchain.require(callResult != null);
            byte[] data = callResult.getReturnData();

            ABIDecoder decoder = new ABIDecoder(data);
            boolean result = decoder.decodeOneBoolean();
            Blockchain.require(result);
        }

        // set up inner interfaces map
        AionMap<ByteArrayWrapper, Address> interfacesImplemented;
        if (interfaces.containsKey(target)) {
            interfacesImplemented = interfaces.get(target);
        } else {
            interfacesImplemented = new AionMap<>();
        }

        // put new interface and implementer into the map
        interfacesImplemented.put(new ByteArrayWrapper(interfaceHash), implementer);
        interfaces.put(target, interfacesImplemented);
        AIRContractEvents.emitInterfaceImplementerSetEvent(target, interfaceHash, implementer);
    }

    /**
     * Setup arguments and calls implementer to check if it implements the given interface
     */
    private static Result checkImplementer(Address implementer, String methodName, Address target, byte[] interfaceHash) {
        byte[] methodNameEncoded = ABIEncoder.encodeOneString(methodName);
        byte[] targetEncoded = ABIEncoder.encodeOneAddress(target);
        byte[] interfaceHashEncoded = ABIEncoder.encodeOneByteArray(interfaceHash);
        byte[] data = ByteArrayHelpers.concatenateMultiple(new byte[][]{methodNameEncoded, targetEncoded, interfaceHashEncoded});

        return Blockchain.call(implementer, BigInteger.ZERO, data, 10_000_000);
    }

    /**
     * Initialization code executed once at the Dapp deployment.
     */
    static {
        managers = new AionMap<>();
        interfaces = new AionMap<>();
    }

    /**
     * Events that this contract emits.
     */

    public static class AIRContractEvents {
        private static String EmitInterfaceImplementerSetEventString = "InterfaceImplementerSetEvent";
        private static String EmitManagerChangedEventString = "ManagerChangedEvent";

        public static void emitInterfaceImplementerSetEvent(Address target, byte[] interfaceHash, Address delegate) {
            byte[][] data = new byte[3][];
            data[0] = target.unwrap();
            data[1] = interfaceHash;
            data[2] = delegate.unwrap();

            Blockchain.log(EmitInterfaceImplementerSetEventString.getBytes(), ByteArrayHelpers.concatenateMultiple(data));
        }

        public static void emitManagerChangedEvent(Address target, Address newManager) {
            Blockchain.log(EmitManagerChangedEventString.getBytes(), ByteArrayHelpers.concatenate(target.unwrap(), newManager.unwrap()));
        }
    }

    /**
     * Helper classes for manipulating byte arrays.
     */
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
