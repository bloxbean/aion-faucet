package com.bloxbean.contracts;

import avm.Address;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.ABIUtil;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.AionSet;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.types.TransactionStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.Assert.fail;

public class AionFaucetContractTest {
    public static final BigInteger ONE_AION = new BigInteger("1000000000000000000");
    @Rule
    public AvmRule avmRule = new AvmRule(true);

    private static long energyLimit = 2000000L;
    private static long energyPrice = 1L;

    private static BigInteger operatorThresholdBalance = new BigInteger("1000000000000000000"); //1 AION
    private static BigInteger operatorTransferBalance = new BigInteger("10000000000000000000"); //10    AION

    private static BigInteger ONETIME_TRANSFER_AMOUNT = new BigInteger("1000000000000000000"); //1 Aion
    private static BigInteger DEFAULT_INITIAL_TOPUP_AMOUNT = new BigInteger("500000000000000000"); //0.5 Aion


    private static BigInteger contractInitialalance = new BigInteger("500000000000000000000");

    private static Address owner = new Address(Helpers.hexStringToBytes("0xa0b5410b6ce75a2880d7e11e74f2b3832579f6813da41af59c931a0ea55414d4"));
    private static Address operator1 = new Address(Helpers.hexStringToBytes("0xa0baf37224a9eacde5c308161298afed14003d99ddb59bb784fc22a2d1d02fc8"));
    private static Address operator2 = new Address(Helpers.hexStringToBytes("0xa0caab7224a9eacde5c308161298afed14003d99ddb59bb784fc22a2d1d02f66"));
    private static Address dev1 = new Address(Helpers.hexStringToBytes("0xa0a0228985614e6368eacfaf01436ddb68c67389c2f5830c95da570594a42da3"));
    private static Address dev2 = new Address(Helpers.hexStringToBytes("0xa0c606b925377d66b087d406eceed90995e17eab443b3e6211cc2b54b9f89aa6"));

    //default address with balance
    private  Address from = avmRule.getPreminedAccount();

    private Address dappAddr;

    @Before
    public void deployDapp() {
        //deploy Dapp:
        // 1- get the Dapp byes to be used for the deploy transaction
        // 2- deploy the Dapp and get the address.

        //Deploy dapp as owner argument
        byte[] deploymentArgs = ABIUtil.encodeDeploymentArguments(owner);
        byte[] dapp = avmRule.getDappBytes(AionFaucetContract.class, deploymentArgs, 1, FaucetEvent.class, AionMap.class, AionSet.class);
        dappAddr = avmRule.deploy(from, BigInteger.ZERO, dapp).getDappAddress();

        //add sufficient aion to the default account
        avmRule.kernel.adjustBalance(new AionAddress(from.toByteArray()), new BigInteger("6000000000000000000000"));

        AvmRule.ResultWrapper result = avmRule.balanceTransfer(from, dappAddr, contractInitialalance, energyLimit, energyPrice);
        Assert.assertTrue(result.getReceiptStatus().isSuccess());

        //Adjust owner's balance
        avmRule.kernel.adjustBalance(new AionAddress(owner.toByteArray()), new BigInteger("100000000000000000"));
    }

    @Test
    public void whenOwnerAddOperatorThenSuccess() {
        byte[] txData = ABIUtil.encodeMethodArguments("addOperator", operator1);
        AvmRule.ResultWrapper result = avmRule.call(owner, dappAddr, BigInteger.ZERO, txData);

        // getReceiptStatus() checks the status of the transaction execution
        TransactionStatus status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        txData = ABIUtil.encodeMethodArguments("getOperators");
        result = avmRule.call(from, dappAddr, BigInteger.ZERO, txData);
        TransactionStatus status1 = result.getReceiptStatus();
        Assert.assertTrue(status1.isSuccess());

        byte[] data = result.getTransactionResult().copyOfTransactionOutput().get();

        Address[] addresses = (Address [])result.getDecodedReturnData();

        Assert.assertEquals(operator1, addresses[0]);

    }

    @Test
    public void whenANonOwnerAddOperatorThenFail() {
        Address nonOwner = new Address(Helpers.hexStringToBytes("0xa0cccc0b6ce75a2880d7e11e74f2b3832579f6813da41af59c931a0ea5541ddd"));
        allocateBalance(nonOwner, "100000000000000000"); //allocate some balance to nonowner

        byte[] txData = ABIUtil.encodeMethodArguments("addOperator", operator1);
        AvmRule.ResultWrapper result = avmRule.call(nonOwner, dappAddr, BigInteger.ZERO, txData);

        // getReceiptStatus() checks the status of the transaction execution
        TransactionStatus status = result.getReceiptStatus();
        Assert.assertTrue(status.isFailed());
    }

    @Test
    public void whenRemoveOperatorThenOperatorNotFoundInList() {
        addOperator(operator1);
        addOperator(operator2);

        Address[] operators = getOperators();

        Assert.assertEquals(2, operators.length);

        //remove
        removeOperator(operator1);
        operators = getOperators();

        Assert.assertEquals(1, operators.length);
        Assert.assertEquals(operator2, operators[0]);
    }

    @Test
    public void whenAddOperatorThenOperatorAssignedWithSomeBalance() {
        BigInteger balance1 = getBalance(operator1);
        BigInteger balance2 = getBalance(operator2);

        Assert.assertEquals(BigInteger.ZERO, balance1);
        Assert.assertEquals(BigInteger.ZERO, balance2);

        addOperator(operator1);
        addOperator(operator2);

        balance1 = getBalance(operator1);
        balance2 = getBalance(operator2);

        BigInteger opExpectedBalance = new BigInteger(String.valueOf("10000000000000000000"));
        Assert.assertEquals(opExpectedBalance, balance1);
        Assert.assertEquals(opExpectedBalance, balance2);

    }


    @Test
    public void whenInitialTransferCallAndMinbalanceThenAutoCreditOperatorAccount() {

        addOperator(operator1);

        avmRule.balanceTransfer(operator1, operator2, new BigInteger("9000000000000000000"), energyLimit, energyPrice); //Transfer to bring down the balance of operator2 to below threshold

        Assert.assertTrue(new BigInteger("1000000000000000000").compareTo(avmRule.kernel.getBalance(new AionAddress(operator1.toByteArray()))) == 1);

        register(operator1, dev1);

        Assert.assertTrue(new BigInteger("1000000000000000000").compareTo(avmRule.kernel.getBalance(new AionAddress(operator1.toByteArray()))) == -1);
    }

    @Test
    public void whenRegisterThenCheckRecipients() {
        addOperator(operator1);
        addOperator(operator2);

        register(operator1, dev1);
        long blockNo = avmRule.kernel.getBlockNumber();

        Assert.assertEquals(1, getTotalRecipients());
        Assert.assertEquals(true, isRecipientAddressRegistered(dev1));
        Assert.assertEquals(false, isRecipientAddressRegistered(dev2));

        //Check block no
        Assert.assertEquals(blockNo, getRecipientLastRequestBlockNo(dev1));
        //check total
        Assert.assertEquals(0, getRecipientRetryCount(dev1));

        avmRule.kernel.generateBlock();
        register(operator1, dev2);
        blockNo = avmRule.kernel.getBlockNumber();

        Assert.assertEquals(2, getTotalRecipients());
        Assert.assertEquals(true, isRecipientAddressRegistered(dev1));
        Assert.assertEquals(true, isRecipientAddressRegistered(dev2));

        //Check block no
        Assert.assertEquals(blockNo, getRecipientLastRequestBlockNo(dev2));
        //check total
        Assert.assertEquals(0, getRecipientRetryCount(dev2));

    }

    @Test
    public void whenTopupWithMaxTryLimitThenOk() {
        setMinBlockDelay(3);
        addOperator(operator1);
        register(operator1, dev1);

        //Default value for initialtopup is 0.5 Aion
        Assert.assertEquals(DEFAULT_INITIAL_TOPUP_AMOUNT, avmRule.kernel.getBalance(new AionAddress(dev1.toByteArray())));

        topup(dev1);
        topup(dev1);
        topup(dev1);

        for(int i=0; i<4; i++)
            avmRule.kernel.generateBlock();

        topup(dev1);
    }

    @Test(expected = AssertionError.class)
    public void whenTopupWithMaxTryLimitThenException() {
        setMinBlockDelay(3);
        addOperator(operator1);
        register(operator1, dev1);
        Assert.assertEquals(DEFAULT_INITIAL_TOPUP_AMOUNT, avmRule.kernel.getBalance(new AionAddress(dev1.toByteArray())));

        topup(dev1);
        topup(dev1);
        topup(dev1);
        topup(dev1);
    }
    @Test
    public void whenTopupThenCheckBalance() {
        setMinBlockDelay(3);
        addOperator(operator1);

        register(operator1, dev1);
        Assert.assertEquals(DEFAULT_INITIAL_TOPUP_AMOUNT, avmRule.kernel.getBalance(new AionAddress(dev1.toByteArray())));

        topup(dev1);
        for(int i=0; i< 4; i++)
            avmRule.kernel.generateBlock();

        topup(dev1);
        topup(dev1);
        topup(dev1);

        Assert.assertTrue(ONETIME_TRANSFER_AMOUNT.compareTo(avmRule.kernel.getBalance(new AionAddress(dev1.toByteArray()))) == -1);

    }

    @Test(expected = AssertionError.class)
    public void whenTopupWithoutRegistrationThenError() {
        setMinBlockDelay(3);
        addOperator(operator1);

        topup(dev1);
    }

    @Test
    public void givenContractBalanceLessThanMinimumWhenTopupThenPublishEvent() {
        addOperator(operator1);
        register(operator1, dev1);

        setContractMinimumBalance(owner, new BigInteger("600")); //contract default bal is 500 Aion
        AvmRule.ResultWrapper resultWrapper = topup(dev1);

        List<Log> logs = resultWrapper.getTransactionResult().logs;

        Assert.assertEquals(2, logs.size());
    }

    @Test
    public void givenContractBalanceMOreThanMinimumWhenTopupThenNoPublishEvent() {
        addOperator(operator1);
        register(operator1, dev1);

        setContractMinimumBalance(owner, new BigInteger("60")); //contract default bal is 500 Aion
        AvmRule.ResultWrapper resultWrapper = topup(dev1);

        List<Log> logs = resultWrapper.getTransactionResult().logs;

        Assert.assertEquals(1, logs.size());
    }


    @Test
    public void whenTopupAmountIsSetThenCheckGetTopupAmount() {

        BigInteger expectedTopupAmount = new BigInteger("5");

        setTopupAmount(owner, expectedTopupAmount);
        BigInteger actualTopupAmount = getTopupAmount();

        Assert.assertEquals(expectedTopupAmount.multiply(ONE_AION), actualTopupAmount);
    }

    @Test
    public void whenTopupAmountIsCalledByNonOwnerThenTransactionFails() {

        BigInteger expectedTopupAmount = new BigInteger("8");

        TransactionStatus status = setTopupAmount(from, expectedTopupAmount);

        Assert.assertEquals(false, status.isSuccess());
    }

    @Test
    public void whenInitialTopupAmountIsSetThenCheckGetInitialTopupAmount() {

        BigInteger expectedTopupAmount = new BigInteger("8");

        setInitialTopupAmount(owner, expectedTopupAmount);
        BigInteger actualTopupAmount = getInitialTopupAmount();

        Assert.assertEquals(expectedTopupAmount.multiply(ONE_AION), actualTopupAmount);
    }

    @Test
    public void whenInitialTopupAmountIsCalledByNonOwnerThenTransactionFails() {

        BigInteger expectedTopupAmount = new BigInteger("8");

        TransactionStatus status = setInitialTopupAmount(from, expectedTopupAmount);

        Assert.assertEquals(false, status.isSuccess());
    }

    @Test
    public void whenContractMinBalanceIsSetThenCheckGetContractMinbalance() {

        BigInteger expectedTopupAmount = new BigInteger("9");

        setContractMinimumBalance(owner, expectedTopupAmount);
        BigInteger actualTopupAmount = getContractMinimumBalance();

        Assert.assertEquals(expectedTopupAmount.multiply(ONE_AION), actualTopupAmount);
    }

    @Test
    public void whenContractMinBalanceIsCalledByNonOwnerThenTransactionFails() {
        BigInteger expectedTopupAmount = new BigInteger("8");

        TransactionStatus status = setContractMinimumBalance(from, expectedTopupAmount);

        Assert.assertEquals(false, status.isSuccess());
    }


    private void addOperator(Address operator) {
        byte[] txData = ABIUtil.encodeMethodArguments("addOperator", operator);
        AvmRule.ResultWrapper result = avmRule.call(owner, dappAddr, BigInteger.ZERO, txData);

        // getReceiptStatus() checks the status of the transaction execution
        TransactionStatus status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
    }

    private void removeOperator(Address operator) {
        //delete operator
        byte[] txData = ABIUtil.encodeMethodArguments("removeOperator", operator);
        AvmRule.ResultWrapper result = avmRule.call(owner, dappAddr, BigInteger.ZERO, txData);

        TransactionStatus status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
    }

    private BigInteger getBalance(Address address) {
        return avmRule.kernel.getBalance(new AionAddress(address.toByteArray()));
    }

    private Address[] getOperators() {
        byte[] txData = ABIUtil.encodeMethodArguments("getOperators");
        AvmRule.ResultWrapper result = avmRule.call(from, dappAddr, BigInteger.ZERO, txData);
        TransactionStatus status1 = result.getReceiptStatus();
        Assert.assertTrue(status1.isSuccess());

        byte[] data = result.getTransactionResult().copyOfTransactionOutput().get();

        Address[] addresses = (Address [])result.getDecodedReturnData();

        return addresses;
    }

    private void register(Address operator, Address dev) {
        byte[] txData = ABIUtil.encodeMethodArguments("registerAddress", dev);
        AvmRule.ResultWrapper result = avmRule.call(operator, dappAddr, BigInteger.ZERO, txData);

        // getReceiptStatus() checks the status of the transaction execution
        TransactionStatus status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
    }

    private AvmRule.ResultWrapper topup(Address dev) {
        byte[] txData = ABIUtil.encodeMethodArguments("topUp");
        AvmRule.ResultWrapper result = avmRule.call(dev, dappAddr, BigInteger.ZERO, txData);

        // getReceiptStatus() checks the status of the transaction execution
        TransactionStatus status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());

        return result;
    }

    private void setMinBlockDelay(long blockDelay) {
        byte[] txData = ABIUtil.encodeMethodArguments("setMinBlockDelay", blockDelay);
        AvmRule.ResultWrapper result = avmRule.call(owner, dappAddr, BigInteger.ZERO, txData);

        // getReceiptStatus() checks the status of the transaction execution
        TransactionStatus status = result.getReceiptStatus();
        Assert.assertTrue(status.isSuccess());
    }

    private long getTotalRecipients() {
        byte[] txData = ABIUtil.encodeMethodArguments("getTotalRecipients");
        AvmRule.ResultWrapper result = avmRule.call(from, dappAddr, BigInteger.ZERO, txData);
        TransactionStatus status1 = result.getReceiptStatus();
        Assert.assertTrue(status1.isSuccess());

        byte[] data = result.getTransactionResult().copyOfTransactionOutput().get();

        long totalRecipients = (long)result.getDecodedReturnData();

        return totalRecipients;
    }

    private boolean isRecipientAddressRegistered(Address address) {
        byte[] txData = ABIUtil.encodeMethodArguments("isAddressRegistered", address);
        AvmRule.ResultWrapper result = avmRule.call(from, dappAddr, BigInteger.ZERO, txData);
        TransactionStatus status1 = result.getReceiptStatus();
        Assert.assertTrue(status1.isSuccess());

        byte[] data = result.getTransactionResult().copyOfTransactionOutput().get();

        boolean returnData = (boolean)result.getDecodedReturnData();

        return returnData;
    }

    private int getRecipientRetryCount(Address address) {
        byte[] txData = ABIUtil.encodeMethodArguments("getRecipientRetryCount", address);
        AvmRule.ResultWrapper result = avmRule.call(from, dappAddr, BigInteger.ZERO, txData);
        TransactionStatus status1 = result.getReceiptStatus();
        Assert.assertTrue(status1.isSuccess());

        byte[] data = result.getTransactionResult().copyOfTransactionOutput().get();

        int retryCount = (int)result.getDecodedReturnData();

        return retryCount;
    }

    private long getRecipientLastRequestBlockNo(Address address) {
        byte[] txData = ABIUtil.encodeMethodArguments("getRecipientLastRequestBlockNo", address);
        AvmRule.ResultWrapper result = avmRule.call(from, dappAddr, BigInteger.ZERO, txData);
        TransactionStatus status1 = result.getReceiptStatus();
        Assert.assertTrue(status1.isSuccess());

        byte[] data = result.getTransactionResult().copyOfTransactionOutput().get();

        long lastRequestBlockNo = (long)result.getDecodedReturnData();

        return lastRequestBlockNo;
    }

    private void allocateBalance(Address address, String balance) {
        avmRule.kernel.adjustBalance(new AionAddress(address.toByteArray()), new BigInteger(balance));
    }

    private TransactionStatus setTopupAmount(Address caller, BigInteger amount) {
        byte[] txData = ABIUtil.encodeMethodArguments("setTopupAmount", amount);
        AvmRule.ResultWrapper result = avmRule.call(caller, dappAddr, BigInteger.ZERO, txData);

        // getReceiptStatus() checks the status of the transaction execution
        TransactionStatus status = result.getReceiptStatus();
        return status;
    }

    private BigInteger getTopupAmount() {
        byte[] txData = ABIUtil.encodeMethodArguments("getTopupAmount");
        AvmRule.ResultWrapper result = avmRule.call(from, dappAddr, BigInteger.ZERO, txData);
        TransactionStatus status1 = result.getReceiptStatus();
        Assert.assertTrue(status1.isSuccess());

        byte[] data = result.getTransactionResult().copyOfTransactionOutput().get();

        BigInteger topupAmount = (BigInteger)result.getDecodedReturnData();

        return topupAmount;
    }

    private TransactionStatus setInitialTopupAmount(Address caller, BigInteger amount) {
        byte[] txData = ABIUtil.encodeMethodArguments("setInitialTopupAmount", amount);
        AvmRule.ResultWrapper result = avmRule.call(caller, dappAddr, BigInteger.ZERO, txData);

        // getReceiptStatus() checks the status of the transaction execution
        TransactionStatus status = result.getReceiptStatus();
        return status;
    }

    private BigInteger getInitialTopupAmount() {
        byte[] txData = ABIUtil.encodeMethodArguments("getInitialTopupAmount");
        AvmRule.ResultWrapper result = avmRule.call(from, dappAddr, BigInteger.ZERO, txData);
        TransactionStatus status1 = result.getReceiptStatus();
        Assert.assertTrue(status1.isSuccess());

        byte[] data = result.getTransactionResult().copyOfTransactionOutput().get();

        BigInteger topupAmount = (BigInteger)result.getDecodedReturnData();

        return topupAmount;
    }

    private TransactionStatus setContractMinimumBalance(Address caller, BigInteger amount) {
        byte[] txData = ABIUtil.encodeMethodArguments("setContractMinimumBalance", amount);
        AvmRule.ResultWrapper result = avmRule.call(caller, dappAddr, BigInteger.ZERO, txData);

        // getReceiptStatus() checks the status of the transaction execution
        TransactionStatus status = result.getReceiptStatus();
        return status;
    }

    private BigInteger getContractMinimumBalance() {
        byte[] txData = ABIUtil.encodeMethodArguments("getContractMinimumBalance");
        AvmRule.ResultWrapper result = avmRule.call(from, dappAddr, BigInteger.ZERO, txData);
        TransactionStatus status1 = result.getReceiptStatus();
        Assert.assertTrue(status1.isSuccess());

        byte[] data = result.getTransactionResult().copyOfTransactionOutput().get();

        BigInteger contractMinBalance = (BigInteger)result.getDecodedReturnData();

        return contractMinBalance;
    }

}

