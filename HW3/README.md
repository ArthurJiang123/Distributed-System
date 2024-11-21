# Setting Up Apache Zookeeper 3.8.4 in IntelliJ

This guide explains how to download and configure Apache Zookeeper 3.8.4 for local development of zookeeper applications in IntelliJ IDEA.

---



1. Download the Zookeeper binary package from the following link:
   [Apache Zookeeper 3.8.4](https://dlcdn.apache.org/zookeeper/zookeeper-3.8.4/apache-zookeeper-3.8.4-bin.tar.gz)

2. Save the file to your desired location and extract it:
   ```bash
   tar -xzvf apache-zookeeper-3.8.4-bin.tar.gz

3. Navigate to: File > Project Structure > Modules.
4. Select your module (on the left) and go to the Dependencies tab.
5. Click the + button on the right and select JARs or Directories.
6. Navigate to the lib folder of your Zookeeper installation and select it.
7. Make sure the scope is set to Compile.