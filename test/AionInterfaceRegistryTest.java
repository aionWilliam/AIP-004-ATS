import AionInterfaceRegistry.AionInterfaceRegistryContract;
import HelperContracts.Interface1ImplementerContract;
import org.aion.avm.api.Address;
import org.aion.avm.tooling.AvmRule;
import org.aion.avm.tooling.hash.HashUtils;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;
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
        byte[] txData = avmRule.getDappBytes(AionInterfaceRegistryContract.class, null);
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
        // set contract2Address as manager for contract1Address
        TransactionResult txResult1 = callSetManager(contract1Address, contract2Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult1.getResultCode());

        // get the manager for contract1Address
        TransactionResult txResult2 = callGetManager(contract1Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());

        // expect the result to be contract2Address
        Address result = (Address) ABIDecoder.decodeOneObject(txResult2.getReturnData());
        Assert.assertEquals(Hex.toHexString(contract2Address.unwrap()), Hex.toHexString(result.unwrap()));
    }

    @Test
    public void testChangeManager() {
        // set contract2Address as manager for contract1Address
        TransactionResult txResult1 = callSetManager(contract1Address, contract2Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult1.getResultCode());

        // set contract3Address as the new manager for contract1Address
        TransactionResult txResult2 = callSetManager(contract1Address, contract3Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());

        // get the manager for contract1Address
        TransactionResult txResult3 = callGetManager(contract1Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult3.getResultCode());

        // expect the result to be contract3Address
        Address result = (Address) ABIDecoder.decodeOneObject(txResult3.getReturnData());
        Assert.assertEquals(Hex.toHexString(contract3Address.unwrap()), Hex.toHexString(result.unwrap()));
    }

    @Test
    public void testGetManagerWhenItHasNotBeenSet() {
        // get the manager for contract1Address
        TransactionResult txResult2 = callGetManager(contract1Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());

        // expect the result to be contract1Address
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

    /** Steps:
     *  - set contract1Address as manager for itself
     *  - set contract1Address as manager for contract2Address
     *  - set contract2Address as manager for contract3Address
     *
     *  - set contract1Address's interfaceImplementer to be contract3Address , called by contract1Address
     *  - set contract2Address's interfaceImplementer to be contract3Address , called by contract1Address
     *  - set contract3Address's interfaceImplementer to be contract1Address , called by contract2Address
     *
     *  - check for correctness
     **/

    @Test
    public void testAIRContract() {
        // set managers
        TransactionResult txResult1 = callSetManager(contract1Address, contract1Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult1.getResultCode());

        TransactionResult txResult2 = callSetManager(contract2Address, contract1Address, contract2Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());

        TransactionResult txResult3 = callSetManager(contract3Address, contract2Address, contract3Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult3.getResultCode());

        // set interface implementers
        TransactionResult txResult4 = callSetInterfaceImplementer(contract1Address, generateInterfaceHash("Interface1"), contract3Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult4.getResultCode());

        TransactionResult txResult5 = callSetInterfaceImplementer(contract2Address, generateInterfaceHash("Interface1"), contract3Address, contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult5.getResultCode());

        TransactionResult txResult6 = callSetInterfaceImplementer(contract3Address, generateInterfaceHash("Interface1"), contract1Address, contract2Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult6.getResultCode());

        // get interface implementers and check if they are all correct
        TransactionResult txResult7 = callGetInterfaceImplementer(contract1Address, generateInterfaceHash("Interface1"), contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult7.getResultCode());
        Assert.assertEquals(Hex.toHexString(contract3Address.unwrap()) , Hex.toHexString(((Address) ABIDecoder.decodeOneObject(txResult7.getReturnData())).unwrap()));

        TransactionResult txResult8 = callGetInterfaceImplementer(contract2Address, generateInterfaceHash("Interface1"), contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult8.getResultCode());
        Assert.assertEquals(Hex.toHexString(contract3Address.unwrap()) , Hex.toHexString(((Address) ABIDecoder.decodeOneObject(txResult8.getReturnData())).unwrap()));

        TransactionResult txResult9 = callGetInterfaceImplementer(contract3Address, generateInterfaceHash("Interface1"), contract1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult9.getResultCode());
        Assert.assertEquals(Hex.toHexString(contract1Address.unwrap()) , Hex.toHexString(((Address) ABIDecoder.decodeOneObject(txResult9.getReturnData())).unwrap()));
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
     * use sha256 hash for hashcode generation
     */
    private byte[] generateInterfaceHash(String interfaceName) {
        return HashUtils.sha256(interfaceName.getBytes());
    }
}

