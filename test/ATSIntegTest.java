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
import java.util.Random;

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

    /**
     * This test will perform the following calls and verify that the contract is working:
     * Setup:
     *   - Create a list of token holder contract.
     *   - Give these contracts some balance so they can pay for gas when making calls.
     *   - Create a list of BigInteger, holder the expected balance for each token holder contract.
     *   - Initialize this list.
     *
     * Calls:
     *   - in a loop, send random amount of tokens, to a randomly picked token holder address.
     *   - record each of the token transfer in our local balance list.
     *   - loop through each token holder and check that the results match.
     *   - verify that the ATS owner has the expected number of tokens
     *   - verify that the total supply is still the right number.
     */
    @Test
    public void testMultifunction() {
        Address[] tokenHolderContract = new Address[10];
        BigInteger[] tokenHolderContractBalance = new BigInteger[10];
        for (int i = 0; i < tokenHolderContract.length; i++) {
            // initialize tokenHolderContract
            byte[] tokenHolderContractCreationData = avmRule.getDappBytes(TokenHolderContract.class, ABIUtil.encodeDeploymentArguments("tokenHolder" + (i)));
            tokenHolderContract[i] = avmRule.deploy(deployer, BigInteger.ZERO, tokenHolderContractCreationData, energyLimit, energyPrice).getDappAddress();
            Assert.assertNotNull(tokenHolderContract[i]);
            avmRule.balanceTransfer(deployer, tokenHolderContract[i], BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);

            // initialize tokenHolderContractBalance
            tokenHolderContractBalance[i] = BigInteger.ZERO;
        }

        // now lets try giving these contracts some tokens
        int loopCount = 1000; // can set this
        for (int i = 0; i < loopCount; i ++) {
            int contractIndex = getRandomNumber(10);
            int tokenAmount = getRandomNumber(1000); // todo: try to make a less uniformed distribution

            // send the tokens from ATS owner
            TransactionResult txResult = callSend(tokenHolderContract[contractIndex], BigInteger.valueOf(tokenAmount).toByteArray(), new byte[0], ATSOwnerAddress);
            Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());
            tokenHolderContractBalance[contractIndex] = tokenHolderContractBalance[contractIndex].add(BigInteger.valueOf(tokenAmount));
        }

        // now lets check that all the token holder contracts has the right amount of tokens
        System.out.println("\nTOKEN HOLDER BALANCE: ");
        int sum = 0;
        for (int i = 0; i < tokenHolderContract.length; i++) {
            // retrieve what ATS contract has in its ledger
            TransactionResult result = callBalanceOf(tokenHolderContract[i], ATSOwnerAddress);
            Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
            BigInteger decodedResult = new BigInteger((byte[]) ABIUtil.decodeOneObject(result.getReturnData()));

            // check if they are equal
            Assert.assertEquals(tokenHolderContractBalance[i], decodedResult);
            System.out.println("TokenHolder" + i + ": " + decodedResult);

            sum += decodedResult.intValue();

            // get the ATS owner balance
            if (i == tokenHolderContract.length -1) {
                result = callBalanceOf(ATSOwnerAddress, ATSOwnerAddress);
                Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
                decodedResult = new BigInteger((byte[]) ABIUtil.decodeOneObject(result.getReturnData()));

                System.out.println("ATS owner: " + decodedResult);
                Assert.assertEquals(ATSTotalSupply.subtract(BigInteger.valueOf(sum)), decodedResult);
            }
        }

        // check that total supply is still as expected
        TransactionResult result = callGetTotalSupply(ATSOwnerAddress);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        BigInteger decodedResult = new BigInteger((byte[]) ABIUtil.decodeOneObject(result.getReturnData()));
        Assert.assertEquals(ATSTotalSupply, decodedResult);
    }

    private int getRandomNumber(int limit) {
        Random random = new Random();
        return  random.nextInt(limit);
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

