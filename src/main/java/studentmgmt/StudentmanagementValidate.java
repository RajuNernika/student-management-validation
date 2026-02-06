package studentmgmt;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.json.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.List;

public class Validate {
    // === PATH CONFIGURATION ===
    private static final String windowsPath = "C:\\Users\\Administrator\\Desktop\\Project";
    private static final String linuxPath = "/home/labuser/Desktop/Project";

    private static ResultOutput resultOutput;
    private static String osName;

    // === SERVICE PATHS (customize based on project structure) ===
    private static final String serviceSubPath = "\\student-management";
    private static final String jarName = "\\target\\student-management.jar";

    // === SERVICE URLs ===
    private static final String serviceBaseUrl = "http://localhost:8080";

    // === DATABASE CONFIGURATION ===
    private static final String dbUrl = "jdbc:mysql://localhost:3306/student-management_db";
    private static final String dbUsername = "root";
    private static final String dbPassword = "root";

    // === ENTITY ID TRACKERS ===
    private static int createdEntityId;

    // === MARKS CONSTANTS ===
    static String MARKS6 = "6";
    static String MARKS8 = "8";
    static String MARKS10 = "10";

    public static void main(String[] args) throws InterruptedException {
        // OS Detection
        String os = System.getProperty("os.name");
        if (os.contains("Windows")) {
            osName = "Windows";
        } else if (os.contains("Linux")) {
            osName = "Linux";
        }

        resultOutput = new ResultOutput(osName);
        resultOutput.init();

        stopServerPort(8080);

        Process serviceProcess = null;

        // === BUILD PHASE ===
        try {
            String servicePath = (osName.equals("Windows") ? windowsPath : linuxPath) + serviceSubPath;
            buildJar(servicePath);

            String jarPath = servicePath + jarName;
            File jarFile = new File(jarPath);

            if (!jarFile.exists()) {
                // Report build failure for ALL testcases
                resultOutput.updateResult(0, "Build Failure", "JAR file not found", "JAR should be built", "Failed", "", MARKS10);
                resultOutput.updateResult(0, "Build Failure", "JAR file not found", "JAR should be built", "Failed", "", MARKS6);
                resultOutput.updateResult(0, "Build Failure", "JAR file not found", "JAR should be built", "Failed", "", MARKS10);
                resultOutput.updateResult(0, "Build Failure", "JAR file not found", "JAR should be built", "Failed", "", MARKS8);
                resultOutput.updateJSON();
                System.exit(1);
            }

            System.out.println("JAR built successfully.");

        } catch (Exception e) {
            e.printStackTrace();
            resultOutput.updateResult(0, "Build Failure", "Exception: " + e.getMessage(), "JAR should be built", "Failed", "", MARKS10);
            resultOutput.updateResult(0, "Build Failure", "Exception: " + e.getMessage(), "JAR should be built", "Failed", "", MARKS6);
            resultOutput.updateResult(0, "Build Failure", "Exception: " + e.getMessage(), "JAR should be built", "Failed", "", MARKS10);
            resultOutput.updateResult(0, "Build Failure", "Exception: " + e.getMessage(), "JAR should be built", "Failed", "", MARKS8);
            resultOutput.updateJSON();
            System.exit(1);
        }

        // === RUN AND TEST PHASE ===
        try {
            String servicePath = (osName.equals("Windows") ? windowsPath : linuxPath) + serviceSubPath;
            String jarPath = servicePath + jarName;

            serviceProcess = runJar(jarPath, 8080, "service.log");

            System.out.println("Waiting for service to start...");
            Thread.sleep(30000);

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // === TEST EXECUTION ===
            System.out.println("Starting test execution...");

            // Call annotation validation first if any
            validateStudentEntityAnnotations();
            validateStudentControllerAnnotations();

            // Then call API validation methods
            validateAddAValidStudent(serviceBaseUrl + "/api/students", "Create a new student with POST request");
            validateGetAllStudents(serviceBaseUrl + "/api/students", "Should return list of students");

            System.out.println("Test execution finished.");

            // === CLEANUP AND REPORTING ===
            System.out.println("Starting cleanup...");
            cleanupDatabase();
            resultOutput.updateJSON();

            if (serviceProcess != null && serviceProcess.isAlive()) {
                serviceProcess.destroyForcibly();
            }
            System.out.println("Process stopped.");
            System.exit(0);
        }
    }

    // === VALIDATION METHODS ===

    private static void validateStudentEntityAnnotations() {
        String expectedResult = "All required annotations present in Student Entity class";
        String description = "Validate Student Entity Annotations";
        try {
            File file = new File((osName.equals("Windows") ? windowsPath : linuxPath) + serviceSubPath + "\\target\\classes");
            URL url = file.toURI().toURL();
            URLClassLoader loader = new URLClassLoader(new URL[]{url});
            Class<?> entityClass = loader.loadClass("com.studentmgmt.entity.Student");

            boolean validationPassed = true;
            StringBuilder failureMessages = new StringBuilder();

            // Check class-level @Entity annotation
            if (!entityClass.isAnnotationPresent(Entity.class)) {
                validationPassed = false;
                failureMessages.append("- @Entity is missing\n");
            }

            // Check class-level @Table annotation
            if (!entityClass.isAnnotationPresent(Table.class)) {
                validationPassed = false;
                failureMessages.append("- @Table is missing\n");
            }

            // Check field annotations using helper method
            validationPassed &= checkFieldAnnotation(entityClass, "id", Id.class, "@Id on id", failureMessages);
            validationPassed &= checkFieldAnnotation(entityClass, "id", GeneratedValue.class, "@GeneratedValue on id", failureMessages);
            validationPassed &= (checkFieldAnnotation(entityClass, "name", NotBlank.class, "@NotBlank on name", failureMessages)
                    || checkFieldAnnotation(entityClass, "name", NotNull.class, "@NotNull on name", failureMessages)
                    || checkFieldAnnotation(entityClass, "name", NotEmpty.class, "@NotEmpty on name", failureMessages));
            validationPassed &= checkFieldAnnotation(entityClass, "email", Email.class, "@Email on email", failureMessages);

            loader.close();

            if (validationPassed) {
                resultOutput.updateResult(1, description, "All annotations validated successfully", expectedResult, "Success", "", MARKS10);
            } else {
                resultOutput.updateResult(0, description, "Annotations missing: " + failureMessages.toString(), expectedResult, "Failed", "", MARKS10);
            }
        } catch (Exception e) {
            resultOutput.updateResult(0, description, "Error loading class: " + e.getMessage(), expectedResult, "Failed", "", MARKS10);
        }
    }

    private static void validateStudentControllerAnnotations() {
        String expectedResult = "All required annotations present in StudentController class";
        String description = "Validate StudentController Annotations";
        try {
            File file = new File((osName.equals("Windows") ? windowsPath : linuxPath) + serviceSubPath + "\\target\\classes");
            URL url = file.toURI().toURL();
            URLClassLoader loader = new URLClassLoader(new URL[]{url});
            Class<?> controllerClass = loader.loadClass("com.studentmgmt.controller.StudentController");

            boolean validationPassed = true;
            StringBuilder failureMessages = new StringBuilder();

            // Check class-level @RestController annotation
            if (!controllerClass.isAnnotationPresent(RestController.class)) {
                validationPassed = false;
                failureMessages.append("- @RestController is missing\n");
            }

            // Check class-level @RequestMapping annotation
            if (!controllerClass.isAnnotationPresent(RequestMapping.class)) {
                validationPassed = false;
                failureMessages.append("- @RequestMapping is missing\n");
            }

            loader.close();

            if (validationPassed) {
                resultOutput.updateResult(1, description, "All annotations validated successfully", expectedResult, "Success", "", MARKS6);
            } else {
                resultOutput.updateResult(0, description, "Annotations missing: " + failureMessages.toString(), expectedResult, "Failed", "", MARKS6);
            }
        } catch (Exception e) {
            resultOutput.updateResult(0, description, "Error loading class: " + e.getMessage(), expectedResult, "Failed", "", MARKS6);
        }
    }

    private static void validateAddAValidStudent(String url, String desc) {
        String expected = "Expected status code 201 and student created successfully";

        try {
            // Build request body as JSON string
            String payload = "{\"name\": \"John Smith\", \"email\": \"john@example.com\", \"grade\": \"A\"}";

            // Make API call using RestAssured
            Response response = RestAssured.given()
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .post(url);

            if (response.getStatusCode() == 201) {
                // Parse response JSON
                JSONObject responseJson = (JSONObject) new JSONParser().parse(response.body().asString());

                // Verify expected fields
                if (responseJson.get("name").equals("John Smith") && responseJson.get("email").equals("john@example.com")) {
                    createdEntityId = ((Long) responseJson.get("id")).intValue();
                    // Optionally verify in database
                    if (checkEntityInDb(createdEntityId, "students")) {
                        resultOutput.updateResult(1, desc, "Student created successfully", expected, "Success", "", MARKS10);
                    }
                } else {
                    resultOutput.updateResult(0, desc, "Field validation failed", expected, "Failed", "", MARKS10);
                }
            } else {
                resultOutput.updateResult(0, desc, "Actual: " + response.getStatusCode(), expected, "Failed", "", MARKS10);
            }
        } catch (Exception ex) {
            resultOutput.updateResult(0, desc, "Exception: " + ex.getMessage(), expected, "Failed", "", MARKS10);
        }
    }

    private static void validateGetAllStudents(String url, String desc) {
        String expected = "Expected status code 200 and list of students returned";

        try {
            // Make API call using RestAssured
            Response response = RestAssured.given()
                    .header("Content-Type", "application/json")
                    .get(url);

            if (response.getStatusCode() == 200) {
                // Parse response JSON
                JSONArray jsonArray = new JSONArray(response.body().asString());
                boolean found = false;
                for (int i = 0; i < jsonArray.length(); i++) {
                    org.json.JSONObject obj = jsonArray.getJSONObject(i);
                    if (obj.get("id") != null && "John Smith".equals(obj.get("name"))) {
                        found = true;
                        break;
                    }
                }

                if (found) {
                    resultOutput.updateResult(1, desc, "List of students returned successfully", expected, "Success", "", MARKS8);
                } else {
                    resultOutput.updateResult(0, desc, "Student not found in list", expected, "Failed", "", MARKS8);
                }
            } else {
                resultOutput.updateResult(0, desc, "Actual: " + response.getStatusCode(), expected, "Failed", "", MARKS8);
            }
        } catch (Exception ex) {
            resultOutput.updateResult(0, desc, "Exception: " + ex.getMessage(), expected, "Failed", "", MARKS8);
        }
    }

    // === UTILITY METHODS ===

    private static String normalizeString(String input) {
        return input.trim().replaceAll("[-,:;._'\"']", "")
                .replaceAll("\\s+", "").toLowerCase();
    }

    private static boolean checkFieldAnnotation(Class<?> clazz, String fieldName,
                                                Class<? extends Annotation> annotationClass, String annotationName,
                                                StringBuilder failureMessages) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            if (!field.isAnnotationPresent(annotationClass)) {
                failureMessages.append("- ").append(annotationName)
                        .append(" is missing on field '").append(fieldName).append("'\n");
                return false;
            }
            return true;
        } catch (NoSuchFieldException e) {
            failureMessages.append("- Field '").append(fieldName).append("' not found\n");
            return false;
        }
    }

    public static String buildJar(String projectPath) {
        ProcessBuilder pb = null;
        if (osName.equalsIgnoreCase("windows")) {
            pb = new ProcessBuilder();
            pb.directory(new File(projectPath));
            pb.command("cmd", "/c", "mvn clean install -DskipTests");
        } else if (osName.equalsIgnoreCase("linux")) {
            pb = new ProcessBuilder();
            pb.directory(new File(projectPath));
            pb.command("mvn", "clean", "install", "-DskipTests");
        }
        if (pb == null) {
            return "Unsupported operating system: " + osName;
        }
        try {
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return "JAR file created successfully.";
            } else {
                return "Failed to create JAR file. Maven build exited with code: " + exitCode;
            }
        } catch (Exception e) {
            return "An error occurred while building the JAR file: " + e.getMessage();
        }
    }

    private static Process runJar(String jarPath, int port, String logFilePath) throws Exception {
        System.out.println("Running " + jarPath + " on port " + port + "...");
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", jarPath, "--server.port=" + port);
        pb.redirectErrorStream(true);
        pb.redirectOutput(new File(logFilePath));
        return pb.start();
    }

    private static void stopServerPort(int portNumber) {
        try {
            if (osName.equals("Windows")) {
                String findCommand = "netstat -ano | findstr :" + portNumber;
                ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", findCommand);
                builder.redirectErrorStream(true);
                Process findProcess = builder.start();
                BufferedReader pidReader = new BufferedReader(new InputStreamReader(findProcess.getInputStream()));
                String line;
                String pid = null;
                while ((line = pidReader.readLine()) != null) {
                    String[] tokens = line.trim().split("\\s+");
                    for (int i = 0; i < tokens.length; i++) {
                        if (tokens[i].equals("LISTENING")) {
                            pid = tokens[tokens.length - 1];
                            break;
                        }
                    }
                    if (pid != null) break;
                }
                pidReader.close();
                if (pid != null && !pid.isEmpty()) {
                    String killCommand = "taskkill /F /PID " + pid;
                    Runtime.getRuntime().exec(killCommand);
                    System.out.println("Port " + portNumber + " has been stopped.");
                }
            } else {
                Runtime.getRuntime().exec("fuser -k " + portNumber + "/tcp");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void cleanupDatabase() {
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
             Statement stmt = conn.createStatement()) {
            // Add cleanup statements for your tables
            // stmt.execute("DELETE FROM table_name");
            System.out.println("Database cleaned.");
        } catch (SQLException e) {
            System.err.println("Error cleaning database: " + e.getMessage());
        }
    }

    private static boolean checkEntityInDb(int id, String tableName) {
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName + " WHERE id = ?")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (SQLException e) {
            System.err.println("Error checking entity in database: " + e.getMessage());
            return false;
        }
    }

    private static void validateLogs(String logFilePath, String[] expectedLogs, String desc) {
        String expected = "Expected log messages should be present in the console output.";
        try {
            String processOutput = new String(Files.readAllBytes(Paths.get(logFilePath)));

            boolean allLogsFound = true;
            StringBuilder missingLogs = new StringBuilder();
            for (String expectedLog : expectedLogs) {
                if (!normalizeString(processOutput).contains(normalizeString(expectedLog))) {
                    allLogsFound = false;
                    missingLogs.append(expectedLog).append("; ");
                }
            }

            if (allLogsFound) {
                resultOutput.updateResult(1, desc, "All expected log messages were found.", expected, "Success", "", MARKS6);
            } else {
                resultOutput.updateResult(0, desc, "Missing logs: " + missingLogs, expected, "Failed", "", MARKS6);
            }
        } catch (IOException ex) {
            resultOutput.updateResult(0, desc, "Exception while validating logs: " + ex.getMessage(), expected, "Failed", "", MARKS6);
        }
    }
}