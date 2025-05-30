# Sistema-de-Votaciones
Step 1: Config the IceGrid
Create folders
- C:/icegrid_data/registry_collocated 
- C:/icegrid_data/node1_collocated 

Step 2: Add electionApp.xml to the IceGrid registry
cd ice-grid
icegridadmin --Ice.Default.Locator="MyElectionGrid/Locator:tcp -h localhost -p 40620" -e "application add C:\ur\pc\directory\to\project\ice-grid\electionApp.xml"                                                   

Step 3: Compile the project
.\gradlew clean build

Step 4: Run the IceGrid registry
cd ice-grid
icegridnode --Ice.Config=config.icegrid