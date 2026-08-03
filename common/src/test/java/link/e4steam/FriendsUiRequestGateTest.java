package link.e4steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FriendsUiRequestGateTest {
    @Test
    void closeAndReopenRejectsThePreviousCompletion() {
        FriendsUiRequestGate gate = new FriendsUiRequestGate();
        gate.open();
        int first = gate.tryBegin();
        assertTrue(gate.isBusy());

        gate.close();
        gate.open();
        int reopened = gate.tryBegin();

        assertFalse(gate.finish(first));
        assertTrue(gate.finish(reopened));
        assertFalse(gate.isBusy());
    }

    @Test
    void fastRepeatedClicksStartOnlyOneOperation() {
        FriendsUiRequestGate gate = new FriendsUiRequestGate();
        gate.open();
        int token = gate.tryBegin();
        assertTrue(token >= 0);
        assertEquals(-1, gate.tryBegin());
        assertTrue(gate.finish(token));
        assertTrue(gate.tryBegin() >= 0);
    }

    @Test
    void closedScreenCannotStartBackgroundWork() {
        FriendsUiRequestGate gate = new FriendsUiRequestGate();
        gate.open();
        gate.close();
        assertFalse(gate.isOpen());
        assertEquals(-1, gate.tryBegin());
    }
}
