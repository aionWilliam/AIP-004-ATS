package HelperContracts;

import avm.Address;

public interface TokenHolderInterface {

    public void tokensReceived(Address operator, Address from, Address to, long amount, byte[] userData, byte[] operatorData);

    public void tokensToSend(Address operator, Address from, Address to, long amount, byte[] userData, byte[] operatorData);
}
