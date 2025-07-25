import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class Database {

    public static void save(
        Map<String, String> authUsers, ReentrantLock authUsersLock,
        Map<Integer, ChatService.ChatRoomInfo> chatRooms, ReentrantLock chatRoomsLock,
        Map<String, Integer> userRoom, ReentrantLock userRoomLock,
        Map<Integer, List<String>> roomsUsers, ReentrantLock roomsUsersLock,
        Map<Integer, List<String>> roomConversations, ReentrantLock conversationLock,
        String filename
    ) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {

            // USERS
            writer.println("[USERS]");
            authUsersLock.lock();
            try {
                for (Map.Entry<String, String> entry : authUsers.entrySet()) {
                    writer.println(entry.getKey() + ":" + entry.getValue());
                }
            } finally { authUsersLock.unlock(); }

            // USER_ROOM
            writer.println("\n[USER_ROOM]");
            userRoomLock.lock();
            try {
                for (Map.Entry<String, Integer> entry : userRoom.entrySet()) {
                    writer.println(entry.getKey() + ":" + entry.getValue());
                }
            } finally { userRoomLock.unlock(); }

            // ROOMS_USERS
            writer.println("\n[ROOMS_USERS]");
            roomsUsersLock.lock();
            try {
                for (Map.Entry<Integer, List<String>> entry : roomsUsers.entrySet()) {
                    writer.print(entry.getKey() + ":");
                    writer.println(String.join(",", entry.getValue()));
                }
            } finally { roomsUsersLock.unlock(); }

            // ROOMS
            writer.println("\n[ROOMS]");
            chatRoomsLock.lock();
            try {
                for (Map.Entry<Integer, ChatService.ChatRoomInfo> entry : chatRooms.entrySet()) {
                    writer.println(entry.getKey() + ":" + entry.getValue().name + ":" + entry.getValue().isAIRoom  + ":" +
                            entry.getValue().initialPrompt.replace("\n", "\\n"));
                }
            } finally { chatRoomsLock.unlock(); }

            // MESSAGES
            writer.println("\n[MESSAGES]");
            conversationLock.lock();
            try {
                for (Map.Entry<Integer, List<String>> entry : roomConversations.entrySet()) {
                    for (String msg : entry.getValue()) {
                        writer.println(entry.getKey() + ":" + msg);
                    }
                }
            } finally { conversationLock.unlock(); }

        } catch (IOException e) {
            System.err.println("Error saving database: " + e.getMessage());
        }
    }

    public static void load(
        Map<String, String> authUsers,
        Map<Integer, ChatService.ChatRoomInfo> chatRooms,
        Map<String, Integer> userRoom,
        Map<Integer, List<String>> roomsUsers,
        Map<Integer, List<String>> roomConversations,
        String filename
    ) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String section = "";
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("[")) {
                    section = line;
                    continue;
                }
                switch (section) {
                    case "[USERS]":
                        String[] userParts = line.split(":", 2);
                        if (userParts.length == 2) {
                            AuthService.refreshToken(userParts[0]);
                            authUsers.put(userParts[0], userParts[1]);
                        }
                        break;
                    case "[USER_ROOM]":
                        String[] urParts = line.split(":", 2);
                        if (urParts.length == 2) {
                            userRoom.put(urParts[0], Integer.parseInt(urParts[1]));
                        }
                        break;
                    case "[ROOMS_USERS]":
                        String[] ruParts = line.split(":", 2);
                        if (ruParts.length == 2) {
                            int roomId = Integer.parseInt(ruParts[0]);
                            List<String> users = new ArrayList<>();
                            if (!ruParts[1].isEmpty()) {
                                for (String token : ruParts[1].split(",")) {
                                    users.add(token);
                                }
                            }
                            roomsUsers.put(roomId, users);
                        }
                        break;
                    case "[ROOMS]":
                        String[] roomParts = line.split(":", 4);
                        if (roomParts.length >= 3) {
                            int roomId = Integer.parseInt(roomParts[0]);
                            String roomName = roomParts[1];
                            boolean isAIRoom = Boolean.parseBoolean(roomParts[2]);
                            String initialPrompt = roomParts.length > 3 ? roomParts[3] : "";
                            chatRooms.put(roomId, new ChatService.ChatRoomInfo(
                                roomName,
                                isAIRoom,
                                initialPrompt.replace("\\n", "\n")
                            ));
                        }
                        break;
                    case "[MESSAGES]":
                        int idx = line.indexOf(":");
                        if (idx > 0) {
                            int roomId = Integer.parseInt(line.substring(0, idx));
                            String msg = line.substring(idx + 1);
                            roomConversations.computeIfAbsent(roomId, k -> new ArrayList<>()).add(msg);
                        }
                        break;
                }
            }
        } catch (IOException e) {
            System.err.println("No database file found, starting fresh.");
        }
    }
}