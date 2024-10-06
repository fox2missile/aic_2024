package kyuu.message;

public interface MessageReceiver {
    // clear the buffer broadcast buffer if rejecting
    void receive(int firstMsg);
}
