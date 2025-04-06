@echo off
if not exist "target\classes" mkdir target\classes

echo Compiling Java files...
javac -d target\classes -cp "lib/*;src/main/java" src/main/java/util/DatabaseConnection.java src/main/java/main/java/SimpleServer.java

if %errorlevel% equ 0 (
    echo Compilation successful!
    echo Running SimpleServer...
    java -cp "target/classes;lib/*" main.java.SimpleServer
) else (
    echo Compilation failed!
    pause
)
