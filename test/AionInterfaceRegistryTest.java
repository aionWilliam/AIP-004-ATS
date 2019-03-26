import AionInterfaceRegistry.AionInterfaceRegistryContract;
import AionInterfaceRegistry.AionInterfaceRegistryInterface;
import Helper.Interface1ImplementerContract;
import org.aion.avm.api.ABIDecoder;
import org.aion.avm.api.ABIEncoder;
import org.aion.avm.api.Address;
import org.aion.avm.tooling.AvmRule;
import org.aion.avm.tooling.hash.HashUtils;
import org.aion.avm.userlib.AionMap;
import org.aion.kernel.AvmTransactionResult;
import org.aion.vm.api.interfaces.TransactionResult;
import org.junit.*;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

public class AionInterfaceRegistryTest {
    @Rule
    public AvmRule avmRule = new AvmRule(true);

    private long energyLimit = 10_000_000L;
    private long energyPrice = 1L;

    private Address deployer = avmRule.getPreminedAccount();
    private Address dappAddress;

    // helper addresses
    private Address contract1Address;
    private Address contract2Address;
    private Address contract3Address;


    @Before
    public void setup() {
        byte[] txData = avmRule.getDappBytes(AionInterfaceRegistryContract.class, null, AionInterfaceRegistryInterface.class, AionMap.class);
        dappAddress = avmRule.deploy(deployer, BigInteger.ZERO, txData, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(dappAddress);

        byte[] txData1 = avmRule.getDappBytes(Interface1ImplementerContract.class, null);
        contract1Address = avmRule.deploy(deployer, BigInteger.ZERO, txData1, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(contract1Address);

        byte[] txData2 = avmRule.getDappBytes(Interface1ImplementerContract.class, null);
        contract2Address = avmRule.deploy(deployer, BigInteger.ZERO, txData2, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(contract2Address);

        byte[] txData3 = avmRule.getDappBytes(Interface1ImplementerContract.class, null);
        contract3Address = avmRule.deploy(deployer, BigInteger.ZERO, txData3, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(contract3Address);

        avmRule.balanceTransfer(deployer, contract1Address, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);
        avmRule.balanceTransfer(deployer, contract2Address, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);
        avmRule.balanceTransfer(deployer, contract3Address, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);
    }

    @Test
    public void testSetAndGetManager() {
        TransactionResult txResult1 = callSetManager(contract1Address, contract2Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult1.getResultCode());

        TransactionResult txResult2 = callGetManager(contract1Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());
    }

    @Test
    public void testGetManagerWhenItHasNotBeenSet() {
        TransactionResult txResult2 = callGetManager(contract1Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());

        Address result = (Address) ABIDecoder.decodeOneObject(txResult2.getReturnData());
        Assert.assertEquals(Hex.toHexString(contract1Address.unwrap()) , Hex.toHexString(result.unwrap()));
    }

    @Test
    public void testSetAndGetInterfaceImplementer() {
        // set contract2Address as the implementer of "Interface1" for contract1Address
        TransactionResult txResult1 = callSetInterfaceImplementer(contract1Address, generateInterfaceHash("Interface1"), contract2Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult1.getResultCode());

        TransactionResult txResult2 = callGetInterfaceImplementer(contract1Address, generateInterfaceHash("Interface1"), contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());

        Address result = (Address) ABIDecoder.decodeOneObject(txResult2.getReturnData());
        Assert.assertEquals(Hex.toHexString(contract2Address.unwrap()), Hex.toHexString(result.unwrap()));
    }

    @Test
    public void testSetSelfAsInterfaceImplementer() {
        // use contract1Address itself as the implementer of "Interface1"
        TransactionResult txResult1 = callSetInterfaceImplementer(contract1Address, generateInterfaceHash("Interface1"), contract1Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult1.getResultCode());

        TransactionResult txResult2 = callGetInterfaceImplementer(contract1Address, generateInterfaceHash("Interface1"), contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());

        Address result = (Address) ABIDecoder.decodeOneObject(txResult2.getReturnData());
        Assert.assertEquals(Hex.toHexString(contract1Address.unwrap()) , Hex.toHexString(result.unwrap()));
    }

    /** ========= AIR Contract Calling Methods========= */
    private TransactionResult callSetManager(Address target, Address newManager, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("setManager", target, newManager);
        return avmRule.call(caller, dappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callGetManager(Address target, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("getManager", target);
        return avmRule.call(caller, dappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callSetInterfaceImplementer(Address target, byte[] interfaceHash, Address implementer, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("setInterfaceImplementer", target, interfaceHash, implementer);
        return avmRule.call(caller, dappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callGetInterfaceImplementer(Address target, byte[] interfaceHash, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("getInterfaceImplementer", target, interfaceHash);
        return avmRule.call(caller, dappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    /**
     * use keccak hash for hashcode generation
     */
    private byte[] generateInterfaceHash(String interfaceName) {
        return HashUtils.sha256(interfaceName.getBytes());
    }
}

