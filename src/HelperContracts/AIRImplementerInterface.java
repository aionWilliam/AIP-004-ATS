package HelperContracts;

import org.aion.avm.api.Address;

public interface AIRImplementerInterface {

    /**
     * Called to get whether or not this address implements the 'interface' hash for the address 'target.
     * @param target Address supporting an interface.
     * @param interfaceHash sha256 hash of the interface name.
     * @return result
     */
    boolean isImplementerFor(Address target, byte[] interfaceHash);
}
