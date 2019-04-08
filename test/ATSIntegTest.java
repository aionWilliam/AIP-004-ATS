import AionInterfaceRegistry.AionInterfaceRegistryContract;
import AionTokenStandard.AionTokenStandardContract;
import HelperContracts.TokenHolderContract;
import avm.Address;
import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.tooling.AvmRule;
import org.aion.avm.tooling.hash.HashUtils;
import org.aion.kernel.AvmTransactionResult;
import org.aion.vm.api.interfaces.TransactionResult;
import org.junit.*;

import java.math.BigInteger;

public class ATSIntegTest {
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
    private Address tokenHolder3Address;
    private String ATSOwnerName = "owner of ATS";
    private String tokenHolder1Name = "token holder 1";
    private String tokenHolder2Name = "token holder 2";
    private String tokenHolder3Name = "token holder 3";

    @Before
    public void setup() {
        // create a token holder contract to be used as the owner to deploy the ATS contract
        byte[] txData5 = avmRule.getDappBytes(TokenHolderContract.class, ABIUtil.encodeDeploymentArguments(ATSOwnerName));
        ATSOwnerAddress = avmRule.deploy(deployer, BigInteger.ZERO, txData5, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(ATSOwnerAddress);
        // give some balance
        avmRule.balanceTransfer(deployer, ATSOwnerAddress, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);

        // deploy AIR
        byte[] txData = avmRule.getDappBytes(AionInterfaceRegistryContract.class, null);
        AIRDappAddress = avmRule.deploy(deployer, BigInteger.ZERO, txData, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(AIRDappAddress);

        // deploy ATS
        byte[] txData2 = avmRule.getDappBytes(AionTokenStandardContract.class, ABIUtil.encodeDeploymentArguments(ATSName, ATSSymbol, ATSGranularity, ATSTotalSupply.toByteArray(), AIRDappAddress));
        ATSDappAddress = avmRule.deploy(ATSOwnerAddress, BigInteger.ZERO, txData2, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(ATSDappAddress);

        // set up token holder contracts and give some balance
        byte[] txData3 = avmRule.getDappBytes(TokenHolderContract.class, ABIUtil.encodeDeploymentArguments(tokenHolder1Name));
        tokenHolder1Address = avmRule.deploy(deployer, BigInteger.ZERO, txData3, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(tokenHolder1Address);

        byte[] txData4 = avmRule.getDappBytes(TokenHolderContract.class, ABIUtil.encodeDeploymentArguments(tokenHolder2Name));
        tokenHolder2Address = avmRule.deploy(deployer, BigInteger.ZERO, txData4, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(tokenHolder2Address);

        byte[] txData6 = avmRule.getDappBytes(TokenHolderContract.class, ABIUtil.encodeDeploymentArguments(tokenHolder3Name));
        tokenHolder3Address = avmRule.deploy(deployer, BigInteger.ZERO, txData6, energyLimit, energyPrice).getDappAddress();
        Assert.assertNotNull(tokenHolder2Address);

        avmRule.balanceTransfer(deployer, AIRDappAddress, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);
        avmRule.balanceTransfer(deployer, ATSDappAddress, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);

        avmRule.balanceTransfer(deployer, tokenHolder1Address, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);
        avmRule.balanceTransfer(deployer, tokenHolder2Address, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);
        avmRule.balanceTransfer(deployer, tokenHolder3Address, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);
    }

    @Test
    public void testMultifunction() {
        BigInteger tokensToSend = BigInteger.valueOf(100);

        // give some tokens to tokenHolder1
        TransactionResult txResult = callSend(tokenHolder1Address, tokensToSend.toByteArray(), new byte[0], ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());

        // sent tokenHolder2 as operator for tokenHolder1

    }

    /** ========= ATS Contract Calling Methods========= */
    private TransactionResult callGetName(Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("getName");
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callGetSymbol(Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("getSymbol");
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callGetTotalSupply(Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("getTotalSupply");
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callGetGranularity(Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("getGranularity");
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callBalanceOf(Address tokenHolder, Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("balanceOf", tokenHolder);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callAuthorizeOperator(Address operator, Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("authorizeOperator", operator);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callRevokeOperatorOperator(Address operator, Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("revokeOperator", operator);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callIsOperatorFor(Address operator, Address tokenHolder, Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("isOperatorFor", operator, tokenHolder);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callSend(Address to, byte[] amount, byte[] data, Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("send", to, amount, data);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callBurn(byte[] amount, byte[] data, Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("burn", amount, data);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callOperatorSend(Address from, Address to, byte[] amount, byte[] data, byte[] operatorData, Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("operatorSend", from, to, amount, data, operatorData);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callOperatorBurn(Address from, byte[] amount, byte[] data, byte[] operatorData, Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("operatorBurn", from, amount, data, operatorData);
        return avmRule.call(caller, ATSDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    /** ========= AIR Contract Calling Methods========= */
    private TransactionResult callSetManager(Address target, Address newManager, Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("setManager", target, newManager);
        return avmRule.call(caller, AIRDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callGetManager(Address target, Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("getManager", target);
        return avmRule.call(caller, AIRDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callSetInterfaceImplementer(Address target, byte[] interfaceHash, Address implementer, Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("setInterfaceImplementer", target, interfaceHash, implementer);
        return avmRule.call(caller, AIRDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    private TransactionResult callGetInterfaceImplementer(Address target, byte[] interfaceHash, Address caller) {
        byte[] txData = ABIUtil.encodeMethodArguments("getInterfaceImplementer", target, interfaceHash);
        return avmRule.call(caller, AIRDappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }

    /**
     * use sha256 hash for hashcode generation
     */
    private byte[] generateInterfaceHash(String interfaceName) {
        return HashUtils.sha256(interfaceName.getBytes());
    }
}

