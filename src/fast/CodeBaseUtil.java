package fast;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class CodeBaseUtil {

    public static Map<String, String> readFiles(String directoryPath) throws IOException {
        Map<String, String> fileContentMap = new HashMap<>();
        Files.walk(Paths.get(directoryPath))
                .filter(Files::isRegularFile)
                .forEach(filePath -> {
                    try {
                        byte[] bytes = Files.readAllBytes(filePath);
                        String content = new String(bytes); // Convert bytes to String
                        fileContentMap.put(filePath.toString(), content);
                    } catch (IOException e) {
                        System.err.println("Error reading file: " + filePath + " - " + e.getMessage());
                    }
                });
        return fileContentMap;
    }

    public static void main(String[] args) throws IOException {
        
        String directoryPath = "src/kyuu"; // Replace with your directory path
        Map<String, String> fileContentMap = readFiles(directoryPath);

        // Print the map content
        fileContentMap.forEach((key, value) -> System.out.println("File: " + key + "\nContent:\n" + value + "\n"));
    }
}