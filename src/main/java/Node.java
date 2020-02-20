

import interfaces.ChordNode;

import java.net.InetSocketAddress;

public class Node implements ChordNode {
    @Override
    public boolean join(InetSocketAddress contact) {
        return false;
    }

    @Override
    public String notify(InetSocketAddress successor) {
        return null;
    }

    @Override
    public void notified(InetSocketAddress newpre) {

    }

    @Override
    public InetSocketAddress findSuccessor(long id) {
        return null;
    }

    @Override
    public InetSocketAddress findClosestPrecedingFinger(long findid) {
        return null;
    }

    @Override
    public void updateFingers(int i, InetSocketAddress value) {

    }

    @Override
    public void clearPredecessor() {

    }

    @Override
    public long getId() {
        return 0;
    }

    @Override
    public InetSocketAddress getAddress() {
        return null;
    }

    @Override
    public InetSocketAddress getPredecessor() {
        return null;
    }

    @Override
    public InetSocketAddress getSuccessor() {
        return null;
    }

    @Override
    public void printNeighbors() {

    }

    @Override
    public void printDataStructure() {

    }

    @Override
    public void stopAllThreads() {

    }
}
