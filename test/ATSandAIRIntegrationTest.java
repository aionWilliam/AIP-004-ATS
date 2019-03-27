import AionInterfaceRegistry.AionInterfaceRegistryContract;
import AionInterfaceRegistry.AionInterfaceRegistryInterface;
import HelperContracts.Interface1ImplementerContract;
import org.aion.avm.api.Address;
import org.aion.avm.tooling.AvmRule;
import org.aion.avm.tooling.hash.HashUtils;
import org.aion.avm.userlib.AionMap;
import org.aion.kernel.AvmTransactionResult;
import org.aion.vm.api.interfaces.TransactionResult;
import org.junit.*;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

public class ATSandAIRIntegrationTest {
    @Rule
    public AvmRule avmRule = new AvmRule(true);

    private long energyLimit = 10_000_000L;
    private long energyPrice = 1L;

    private Address deployer = avmRule.getPreminedAccount();
    private Address dappAddress;

    // helper addresses
    private Address contract1Address;
    private Address contract2Address;


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

        avmRule.balanceTransfer(deployer, contract1Address, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);
        avmRule.balanceTransfer(deployer, contract2Address, BigInteger.valueOf(1_000_000_000L), energyLimit, energyPrice);
    }



    /**
     * use sha256 hash for hashcode generation
     */
    private byte[] generateInterfaceHash(String interfaceName) {
        return HashUtils.sha256(interfaceName.getBytes());
    }
}

