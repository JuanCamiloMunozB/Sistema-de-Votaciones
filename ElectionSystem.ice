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
        int citizenId;
        int candidateId;
        string tableCode;
        string timestamp;
    }

    sequence<CandidateData> CandidateDataSeq;
    sequence<CitizenData> CitizenDataSeq;

    struct VotingTableData {
        int id;
        string code;
        string location;
        CitizenDataSeq citizens;
    }

    sequence<VotingTableData> VotingTableDataSeq;

    struct ElectionData {
        string name;
        string startDate;
        string endDate;
        CandidateDataSeq candidates;
        VotingTableDataSeq tables;
    }

    interface ServerService {
        ElectionData getElectionData(string controlCenterId);
        void registerVote(VoteData vote);
    }

    interface ControlCenterService {
        VotingTableData getVotingTableData(int tableId);
        CandidateDataSeq getCandidates();
        void startElection();
        void endElection();
        void submitVote(VoteData vote);
    }

    interface VotingTableService {
        void emitVote(VoteData vote);
    }
}