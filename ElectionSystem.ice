module ElectionSystem {

    struct CandidateData {
        int id;
        string firstName;
        string lastName;
        string party;
    }

    struct CitizenData {
        int id;
        string document;
        string firstName;
        string lastName;
        int tableId;
    }

    struct VoteData {
        string citizenDocument;
        int candidateId;
        int tableId;
        string timestamp;
    }

    struct CandidateResult {
    int candidateId;
    string candidateName;
    int totalVotes;
    };

    sequence<CandidateData> CandidateDataSeq;
    sequence<CitizenData> CitizenDataSeq;
    sequence<CandidateResult> CandidateResultSeq;

    dictionary<int, CandidateResultSeq> TableResultsMap;

    struct VotingTableData {
        int id;
        CitizenDataSeq citizens;
    }

    sequence<VotingTableData> VotingTableDataSeq;

    struct ElectionData {
        string name;
        string startDate;
        string endDate;
        CandidateDataSeq candidates;
    }

    enum EventType {
        ElectionStarted,
        ElectionEnded,
        VoteRegistered
    }
    dictionary<string, string> EventPayload;
    struct ElectionEvent {
        EventType type;
        string timestamp;
        EventPayload details;
    };
    
    interface EventObserver {
        void notify(ElectionEvent event);
    };

    interface ElectionActivityObserver {
        void electionStarted();
        void electionEnded();
    };

    exception ElectionInactive {
        string reason;
    };

    exception CitizenAlreadyVoted {
        string reason;
    };

    exception CitizenNotFound {
        string reason;
    };

    exception CandidateNotFound {
        string reason;
    };

    exception CitizenNotBelongToTable {
        string reason;
    };

    interface ServerService {
        ElectionData getElectionData(int controlCenterId);
        VotingTableDataSeq getVotingTablesFromStation(int controlCenterId);
        void registerVote(VoteData vote) throws CitizenAlreadyVoted, CitizenNotFound, CandidateNotFound, CitizenNotBelongToTable;
        void subscribe(EventObserver* observer, string observerIdentity);
        void unsubscribe(string observerIdentity);
        CandidateDataSeq getCandidates();
        string findVotingStationByDocument(string document);
        CandidateResultSeq getGlobalResults();
        TableResultsMap getResultsByVotingTable();
        CitizenDataSeq getCitizensByTableId(int tableId);
        string getProcessingStats();
        void printQueueStatus();
        string getCacheStats();
        void clearCache();
    }

    interface ControlCenterService {
        VotingTableData getVotingTableData(int tableId);
        CandidateDataSeq getCandidates();
        void startElection();
        void endElection();
        void submitVote(VoteData vote) throws CitizenAlreadyVoted, CitizenNotFound, CandidateNotFound, CitizenNotBelongToTable, ElectionInactive;
        void subscribeElectionActivity(ElectionActivityObserver* observer, string votingTableIdentity);
        void unsubscribeElectionActivity(string votingTableIdentity);
        CitizenDataSeq getCitizensByTableId(int tableId);
    }

    interface VoteStation{
        int vote(string document, int candidateId);
    }

    interface VotingTableService {
        void emitVote(VoteData vote) throws ElectionInactive, CitizenAlreadyVoted, CitizenNotFound, CandidateNotFound, CitizenNotBelongToTable;
    }

    interface VotingTableCombinedService extends VotingTableService, ElectionActivityObserver, VoteStation {
    }

    interface queryStation {
        string query(string document);
    }

    interface ServerQueryService {
        string findVotingStationByDocument(string document);
    }
}