import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ChatPacket implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        LOGIN,
        LOGIN_RESPONSE,
        MESSAGE,
        PRIVATE_MESSAGE,
        ECHO,
        USER_LIST,
        ROOM_LIST,
        CREATE_ROOM,
        JOIN_ROOM,
        LEAVE_ROOM,
        TYPING,
        FILE_DATA,
        SYSTEM,
        HISTORY
    }

    public Type type;

    public String username = "";
    public String password = "";
    public String receiver = "";
    public String room = "";
    public String message = "";
    public String timestamp = "";
    public String fileName = "";

    public byte[] fileData;

    public boolean success = false;

    public List<String> users = new ArrayList<String>();
    public List<String> rooms = new ArrayList<String>();
    public List<String> history = new ArrayList<String>();

    public ChatPacket(Type type) {
        this.type = type;
    }
}
