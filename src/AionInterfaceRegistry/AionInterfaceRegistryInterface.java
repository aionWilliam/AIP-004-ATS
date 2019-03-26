package AionInterfaceRegistry;

import org.aion.avm.api.Address;

public interface AionInterfaceRegistryInterface {

    public Address getManager(Address target);

    public void setManager(Address target, Address newManager);

    public Address getInterfaceImplementer(Address target, byte[] interfaceHash);

    public void setInterfaceImplementer(Address target, byte[] interfaceHash, Address delegate);
}
