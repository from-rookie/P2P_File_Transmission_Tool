package storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import model.User;

public class FileStorage {
    private static final String USER_DATA_FILE = "users.dat";
    private static FileStorage instance = new FileStorage();

    private FileStorage() {}

    public static FileStorage getInstance() {
        return instance;
    }

    public synchronized Map<String, User> loadUsers() {
        Map<String, User> users = new HashMap<>();
        File file = new File(USER_DATA_FILE);
        
        if (!file.exists()) {
            return users;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            users = (Map<String, User>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        
        return users;
    }

    public synchronized void saveUsers(Map<String, User> users) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(USER_DATA_FILE))) {
            oos.writeObject(users);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}