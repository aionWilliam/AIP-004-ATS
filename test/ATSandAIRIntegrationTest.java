import AionInterfaceRegistry.AionInterfaceRegistryContract;
import AionTokenStandard.AionTokenStandardContract;
import HelperContracts.TokenHolderContract;
import org.aion.avm.api.Address;
import org.aion.avm.tooling.AvmRule;
import org.aion.avm.tooling.hash.HashUtils;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.kernel.AvmTransactionResult;
import org.aion.vm.api.interfaces.TransactionResult;
import org.junit.*;

import java.math.BigInteger;

public class ATSandAIRIntegrationTest {
    @Rule
    public AvmRule avmRule = new AvmRule(true);

    private long energyLimit = 10_000_000L;
    private long energyPrice = 1L;

    private Address deployer = avmRule.getPreminedAccount();

    // ATS deployment variables
    private Address ATSDappAddress;
    private final String ATSName = "my ats name";
    private final String ATSSymbol = "my ats symbol";
    private final int ATSGranularity = 1;
    private final BigInteger ATSTotalSupply = BigInteger.valueOf(1_000_000);

    // helpers
    private Address AIRDappAddress;

    private Address ATSOwnerAddress;
    private Address tokenHolder1Address;
    private Address tokenHolder2Address;
    private String ATSOwnerName = "owner of ATS";
    private String tokenHolder1Name = "token holder 1";
    private String tokenHolder2Name = "token holder 2";

    @Before
    public void setup() {
        // create a token holder contract to be used as the owner to deploy the ATS contract
        byte[] txData5 = avmRule.getDappBytes(TokenHolderContract.class, ABIEncoder.encodeDeploymentArguments(ATSOwnerName));
        ATSOwnerAddress = avmRule.deploy(deployer, BigInteger.ZERO, txData5, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(ATSOwnerAddress);
        // give some balance
        avmRule.balanceTransfer(deployer, ATSOwnerAddress, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);

        // deploy AIR
        byte[] txData = avmRule.getDappBytes(AionInterfaceRegistryContract.class, null);
        AIRDappAddress = avmRule.deploy(deployer, BigInteger.ZERO, txData, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(AIRDappAddress);

        // deploy ATS
        byte[] txData2 = avmRule.getDappBytes(AionTokenStandardContract.class, ABIEncoder.encodeDeploymentArguments(ATSName, ATSSymbol, ATSGranularity, ATSTotalSupply.toByteArray(), AIRDappAddress));
        ATSDappAddress = avmRule.deploy(ATSOwnerAddress, BigInteger.ZERO, txData2, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(ATSDappAddress);

        // set up token holder contracts and give some balance
        byte[] txData3 = avmRule.getDappBytes(TokenHolderContract.class, ABIEncoder.encodeDeploymentArguments(tokenHolder1Name));
        tokenHolder1Address = avmRule.deploy(deployer, BigInteger.ZERO, txData3, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(tokenHolder1Address);

        byte[] txData4 = avmRule.getDappBytes(TokenHolderContract.class, ABIEncoder.encodeDeploymentArguments(tokenHolder2Name));
        tokenHolder2Address = avmRule.deploy(deployer, BigInteger.ZERO, txData4, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(tokenHolder2Address);

        avmRule.balanceTransfer(deployer, AIRDappAddress, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);
        avmRule.balanceTransfer(deployer, ATSDappAddress, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);

        avmRule.balanceTransfer(deployer, tokenHolder1Address, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);
        avmRule.balanceTransfer(deployer, tokenHolder2Address, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);
    }

    @Test
    public void testGetName() {
        TransactionResult txResult1 = callGetName(deployer);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult1.getResultCode());

        String decodedResult = (String) ABIDecoder.decodeOneObject(txResult1.getReturnData());
        Assert.assertEquals(ATSName, decodedResult);
    }

    @Test
    public void testGetSymbol() {
        TransactionResult txResult = callGetSymbol(deployer);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());

        String decodedResult = (String) ABIDecoder.decodeOneObject(txResult.getReturnData());
        Assert.assertEquals(ATSSymbol, decodedResult);
    }

    @Test
    public void testGetTotalSupply() {
        TransactionResult txResult = callGetTotalSupply(deployer);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());

        BigInteger decodedResult = new BigInteger((byte[]) ABIDecoder.decodeOneObject(txResult.getReturnData()));
        Assert.assertEquals(ATSTotalSupply, decodedResult);
    }

    @Test
    public void testGetGranularity() {
        TransactionResult txResult = callGetGranularity(deployer);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());

        int decodedResult = (int) ABIDecoder.decodeOneObject(txResult.getReturnData());
        Assert.assertEquals(ATSGranularity, decodedResult);
    }

    @Test
    public void testCheckBalanceOfUnregisteredAddress() {
        TransactionResult txResult = callBalanceOf(tokenHolder1Address, tokenHolder1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());

        BigInteger decodedResult = new BigInteger((byte[]) ABIDecoder.decodeOneObject(txResult.getReturnData()));
        Assert.assertEquals(BigInteger.ZERO, decodedResult);
    }

    @Test
    public void testCheckBalanceOfOwnerUponDappDeployment() {
        TransactionResult txResult = callBalanceOf(ATSOwnerAddress, ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());

        BigInteger decodedResult = new BigInteger((byte[]) ABIDecoder.decodeOneObject(txResult.getReturnData()));
        Assert.assertEquals(ATSTotalSupply, decodedResult);
    }

    @Test
    public void testAuthorizeAndRevokeOperator() {
        TransactionResult txResult = callAuthorizeOperator(tokenHolder2Address, tokenHolder1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());

        TransactionResult txResult2 = callIsOperatorFor(tokenHolder2Address, tokenHolder1Address, ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());

        boolean decodedResult = (boolean) ABIDecoder.decodeOneObject(txResult2.getReturnData());
        Assert.assertTrue(decodedResult);

        TransactionResult txResult3 = callRevokeOperatorOperator(tokenHolder2Address, tokenHolder1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult3.getResultCode());

        TransactionResult txResult4 = callIsOperatorFor(tokenHolder2Address, tokenHolder1Address, ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult4.getResultCode());

        boolean decodedResult2 = (boolean) ABIDecoder.decodeOneObject(txResult4.getReturnData());
        Assert.assertFalse(decodedResult2);
    }

    @Test
    public void testSend() {
        BigInteger tokensToSend = BigInteger.valueOf(100);
        byte[] senderData = "sending 100 tokens to tokenHolder1Address".getBytes();

        // send 100 tokens from owner address to tokenHolder1Address
        TransactionResult txResult = callSend(tokenHolder1Address, tokensToSend.toByteArray(), senderData, ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());

        // check token balance of tokenHolder1Address
        TransactionResult txResult2 = callBalanceOf(tokenHolder1Address, tokenHolder1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());

        BigInteger decodedResult = new BigInteger((byte[]) ABIDecoder.decodeOneObject(txResult2.getReturnData()));
        Assert.assertEquals(tokensToSend, decodedResult);

        // check token balance of owner address
        TransactionResult txResult3 = callBalanceOf(ATSOwnerAddress, ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult3.getResultCode());

        BigInteger decodedResult2 = new BigInteger((byte[]) ABIDecoder.decodeOneObject(txResult3.getReturnData()));
        Assert.assertEquals(ATSTotalSupply.subtract(tokensToSend), decodedResult2);
    }

    @Test
    public void testBurn() {
        BigInteger tokensToBurn = BigInteger.valueOf(100);
        byte[] senderData = "burning 100 tokens".getBytes();

        // burn 100 tokens from ATSOwnerAddress
        TransactionResult txResult = callBurn(tokensToBurn.toByteArray(), senderData, ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());

        // check token balance of ATSOwnerAddress
        TransactionResult txResult2 = callBalanceOf(ATSOwnerAddress, ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());

        BigInteger decodedResult = new BigInteger((byte[]) ABIDecoder.decodeOneObject(txResult2.getReturnData()));
        Assert.assertEquals(ATSTotalSupply.subtract(tokensToBurn), decodedResult);

        // check the decrease in totalSupply
        TransactionResult txResult3 = callGetTotalSupply(ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult3.getResultCode());

        BigInteger decodedResult2 = new BigInteger((byte[]) ABIDecoder.decodeOneObject(txResult3.getReturnData()));
        Assert.assertEquals(ATSTotalSupply.subtract(tokensToBurn), decodedResult2);
    }

    @Test
    public void testOperatorSend() {
        BigInteger tokensToSend = BigInteger.valueOf(100);
        byte[] senderData = "".getBytes();
        byte[] operatorData = "sending 100 tokens to tokenHolder1Address on behalf of ATSOwnerAddress".getBytes();

        // send 100 tokens from ATSOwner to tokenHolder1Address
        TransactionResult txResult = callOperatorSend(ATSOwnerAddress, tokenHolder1Address, tokensToSend.toByteArray(), senderData, operatorData, ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());

        // check token balance of tokenHolder1Address
        TransactionResult txResult2 = callBalanceOf(tokenHolder1Address, tokenHolder1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());

        BigInteger decodedResult = new BigInteger((byte[]) ABIDecoder.decodeOneObject(txResult2.getReturnData()));
        Assert.assertEquals(tokensToSend, decodedResult);

        // check token balance of ATSOwner
        TransactionResult txResult3 = callBalanceOf(ATSOwnerAddress, ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult3.getResultCode());

        BigInteger decodedResult2 = new BigInteger((byte[]) ABIDecoder.decodeOneObject(txResult3.getReturnData()));
        Assert.assertEquals(ATSTotalSupply.subtract(tokensToSend), decodedResult2);
    }

    @Test
    public void testOperatorBurn() {
        BigInteger tokensToBurn = BigInteger.valueOf(100);
        byte[] senderData = "".getBytes();
        byte[] operatorData = "burning 100 tokens".getBytes();

        // burn 100 tokens from ATSOwnerAddress
        TransactionResult txResult = callOperatorBurn(ATSOwnerAddress, tokensToBurn.toByteArray(), senderData, operatorData, ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());

        // check token balance of ATSOwnerAddress
        TransactionResult txResult2 = callBalanceOf(ATSOwnerAddress, ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());

        BigInteger decodedResult = new BigInteger((byte[]) ABIDecoder.decodeOneObject(txResult2.getReturnData()));
        Assert.assertEquals(ATSTotalSupply.subtract(tokensToBurn), decodedResult);

        // check the decrease in totalSupply
        TransactionResult txResult3 = callGetTotalSupply(ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult3.getResultCode());

        BigInteger decodedResult2 = new BigInteger((byte[]) ABIDecoder.decodeOneObject(txResult3.getReturnData()));
        Assert.assertEquals(ATSTotalSupply.subtract(tokensToBurn), decodedResult2);
    }

    @Test
    public void testSenderHaveNoBalance() {
        BigInteger tokensToSend = BigInteger.valueOf(100);
        byte[] senderData = "sending 100 tokens to tokenHolder1Address".getBytes();

        // send 100 tokens from tokenHolder1Address to tokenHolder2Address
        TransactionResult txResult = callSend(tokenHolder2Address, tokensToSend.toByteArray(), senderData, tokenHolder1Address);
        Assert.assertEquals(AvmTransactionResult.Code.FAILED_REVERT, txResult.getResultCode());
    }

    @Test
    public void testSenderHaveNoBalanceButSendsZero() {
        BigInteger tokensToSend = BigInteger.ZERO;
        byte[] senderData = "sending 100 tokens to tokenHolder1Address".getBytes();

        // send 100 tokens from tokenHolder1Address to tokenHolder2Address
        TransactionResult txResult = callSend(tokenHolder2Address, tokensToSend.toByteArray(), senderData, tokenHolder1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());

        // check token balance of tokenHolder1Address
        TransactionResult txResult2 = callBalanceOf(tokenHolder1Address, tokenHolder1Address);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult2.getResultCode());

        BigInteger decodedResult = new BigInteger((byte[]) ABIDecoder.decodeOneObject(txResult2.getReturnData()));
        Assert.assertEquals(BigInteger.ZERO, decodedResult);
    }

    @Test
    public void testSenderInsufficientBalance() {
        BigInteger tokensToSend = BigInteger.valueOf(100);
        BigInteger tokensToSend2 = BigInteger.valueOf(200);
        byte[] senderData = "sending 100 tokens to tokenHolder1Address".getBytes();

        // send 100 tokens from ATSOwnerAddress to tokenHolder1Address
        TransactionResult txResult = callSend(tokenHolder1Address, tokensToSend.toByteArray(), senderData, ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());

        // try to send 200 tokens from tokenHolder1Address to tokenHolder2Address
        TransactionResult txResult2 = callSend(tokenHolder2Address, tokensToSend2.toByteArray(), senderData, tokenHolder1Address);
        Assert.assertEquals(AvmTransactionResult.Code.FAILED_REVERT, txResult2.getResultCode());
    }

    /** ========= ATS Contract Calling Methods========= */
    private TransactionResult callGetName(Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("getName");
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callGetSymbol(Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("getSymbol");
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callGetTotalSupply(Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("getTotalSupply");
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callGetGranularity(Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("getGranularity");
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callBalanceOf(Address tokenHolder, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("balanceOf", tokenHolder);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callAuthorizeOperator(Address operator, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("authorizeOperator", operator);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callRevokeOperatorOperator(Address operator, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("revokeOperator", operator);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callIsOperatorFor(Address operator, Address tokenHolder, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("isOperatorFor", operator, tokenHolder);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callSend(Address to, byte[] amount, byte[] data, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("send", to, amount, data);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callBurn(byte[] amount, byte[] data, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("burn", amount, data);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callOperatorSend(Address from, Address to, byte[] amount, byte[] data, byte[] operatorData, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("operatorSend", from, to, amount, data, operatorData);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callOperatorBurn(Address from, byte[] amount, byte[] data, byte[] operatorData, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("operatorBurn", from, amount, data, operatorData);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    /** ========= AIR Contract Calling Methods========= */
    private TransactionResult callSetManager(Address target, Address newManager, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("setManager", target, newManager);
        return avmRule.call(caller, AIRDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callGetManager(Address target, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("getManager", target);
        return avmRule.call(caller, AIRDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callSetInterfaceImplementer(Address target, byte[] interfaceHash, Address implementer, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("setInterfaceImplementer", target, interfaceHash, implementer);
        return avmRule.call(caller, AIRDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callGetInterfaceImplementer(Address target, byte[] interfaceHash, Address caller) {
        byte[] txData = ABIEncoder.encodeMethodArguments("getInterfaceImplementer", target, interfaceHash);
        return avmRule.call(caller, AIRDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    /**
     * use sha256 hash for hashcode generation
     */
    private byte[] generateInterfaceHash(String interfaceName) {
        return HashUtils.sha256(interfaceName.getBytes());
    }
}

