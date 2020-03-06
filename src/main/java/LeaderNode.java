import interfaces.ChordNode;

import java.net.InetSocketAddress;
import java.util.Map;

public class LeaderNode implements ChordNode{
    private ChordNode node;

    public LeaderNode(ChordNode node) {
        this.node = node;
    }

    @Override
    public boolean join(InetSocketAddress contact) {
        return this.node.join(contact);
    }

    @Override
    public String notify(InetSocketAddress successor) {
        return null;
    }

    @Override
    public void notified(InetSocketAddress newpre) {

    }

    @Override
    public void joinAndHint(InetSocketAddress newSucc, InetSocketAddress oldPredOfSucc) {

    }

    @Override
    public void hinted(InetSocketAddress successor) {

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
    public long getNodeId() {
        return 0;
    }

    @Override
    public InetSocketAddress getAddress() {
        return null;
    }

    @Override
    public InetSocketAddress getPredecessor1() {
        return null;
    }

    @Override
    public InetSocketAddress getSuccessor1() {
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

    @Override
    public String getJoinState() {
        return null;
    }

    @Override
    public boolean isLocked() {
        return false;
    }

    @Override
    public String getNodeIpAddress() {
        return null;
    }

    @Override
    public int getPort() {
        return 0;
    }

    @Override
    public void lock() {

    }

    @Override
    public void unlock() {

    }

    @Override
    public String setNewSucc(InetSocketAddress successor) {
        return null;
    }

    @Override
    public void updateNewPre(InetSocketAddress predecessor) {

    }

    @Override
    public Map<String, String> getDataStore() {
        return null;
    }

    @Override
    public void setDataStore(Map<String, String> dataStore) {

    }
}
