package com.bloxbean.contracts;

import avm.Address;
import avm.Blockchain;

import java.math.BigInteger;

public class FaucetEvent {

    public static void operatorAdded(Address operator) {
        Blockchain.log("OperatorAdded".getBytes(), operator.toByteArray());
    }

    public static void operatorRemoved(Address operator) {
        Blockchain.log("OperatorRemoved".getBytes(), operator.toByteArray());
    }

    public static void addressRegistered(Address address) {
        Blockchain.log("AddressRegistered".getBytes(), address.toByteArray());
    }

    public static void topup(Address address, BigInteger balance) {
        Blockchain.log("TopUp".getBytes(), address.toByteArray(), String.valueOf(balance).getBytes());
    }

    public static void balanceBelowMinimum(BigInteger balance) {
        Blockchain.log("MinimumBalance".getBytes(), String.valueOf(balance).getBytes());
    }

}
