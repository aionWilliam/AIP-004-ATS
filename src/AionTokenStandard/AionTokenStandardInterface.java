package AionTokenStandard;

import org.aion.avm.api.Address;

public interface AionTokenStandardInterface {

    /**
     * Basic token functionality
     */

    public String getName();

    public String getSymbol();

    public int granularity();

    public long totalSupply();

    public long balanceOf(Address address);

    /**
     * ERC-777 operator
     */

    public void authorizeOperator(Address operator);

    public void revokeOperator(Address operator);

    public boolean isOperatorFor(Address operator, Address tokenHolder);

    /**
     * Token management
     */

    public void send(Address to, long amount, byte[] data);

    public void burn(long amount);

    public void operatorSend(Address from, Address to, long amount, byte[] data, byte[] operatorData);

    public void operatorBurn(Address from, long amount, byte[] operatorData);

    /**
     * Cross-chain functionalities
     */

    public long liquidSupply();

    public void thaw(Address localRecipient, long amount, byte[] bridgeId, byte[] bridgeData, byte[] removeSender, byte[] remoteData);

    public void freeze(byte[] remoteRecipient, long amount, byte[] bridgeId, byte[] localData);

    public void operatorFreeze(Address localSender, byte[] remoteRecipient, long amount, byte[] bridgeId, byte[] localData);
}
