# Sistema-de-Votaciones
Step 1: Compile the project
.\gradlew clean build

Step 2: Run the IceGrid registry
cd ice-grid
icegridregistry --Ice.Config=config.registry

Step 3: Add electionApp.xml to the IceGrid registry
In other terminal
cd ice-grid
icegridadmin --Ice.Default.Locator="IceGrid/Locator:tcp -h localhost -p 40620" -e "application add electionApp.xml"                                          

Step 4: Run the IceGrid node
On the same terminal as Step 3
icegridnode --Ice.Config=config.icegrid

Step 5: Run the VotingTable servers
In another terminal (now you have three terminals open)
java -DVOTING_TABLE_ID=3 -cp "voting_table/build/classes/java/main;jar-files/ice-3.7.9.jar;." VotingTableMain