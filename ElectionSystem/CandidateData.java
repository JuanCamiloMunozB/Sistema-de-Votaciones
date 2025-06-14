//
// Copyright (c) ZeroC, Inc. All rights reserved.
//
//
// Ice version 3.7.10
//
// <auto-generated>
//
// Generated from file `ElectionSystem.ice'
//
// Warning: do not edit this file.
//
// </auto-generated>
//

package ElectionSystem;

public class CandidateData implements java.lang.Cloneable,
                                      java.io.Serializable
{
    public int id;

    public String firstName;

    public String lastName;

    public String party;

    public CandidateData()
    {
        this.firstName = "";
        this.lastName = "";
        this.party = "";
    }

    public CandidateData(int id, String firstName, String lastName, String party)
    {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.party = party;
    }

    public boolean equals(java.lang.Object rhs)
    {
        if(this == rhs)
        {
            return true;
        }
        CandidateData r = null;
        if(rhs instanceof CandidateData)
        {
            r = (CandidateData)rhs;
        }

        if(r != null)
        {
            if(this.id != r.id)
            {
                return false;
            }
            if(this.firstName != r.firstName)
            {
                if(this.firstName == null || r.firstName == null || !this.firstName.equals(r.firstName))
                {
                    return false;
                }
            }
            if(this.lastName != r.lastName)
            {
                if(this.lastName == null || r.lastName == null || !this.lastName.equals(r.lastName))
                {
                    return false;
                }
            }
            if(this.party != r.party)
            {
                if(this.party == null || r.party == null || !this.party.equals(r.party))
                {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    public int hashCode()
    {
        int h_ = 5381;
        h_ = com.zeroc.IceInternal.HashUtil.hashAdd(h_, "::ElectionSystem::CandidateData");
        h_ = com.zeroc.IceInternal.HashUtil.hashAdd(h_, id);
        h_ = com.zeroc.IceInternal.HashUtil.hashAdd(h_, firstName);
        h_ = com.zeroc.IceInternal.HashUtil.hashAdd(h_, lastName);
        h_ = com.zeroc.IceInternal.HashUtil.hashAdd(h_, party);
        return h_;
    }

    public CandidateData clone()
    {
        CandidateData c = null;
        try
        {
            c = (CandidateData)super.clone();
        }
        catch(CloneNotSupportedException ex)
        {
            assert false; // impossible
        }
        return c;
    }

    public void ice_writeMembers(com.zeroc.Ice.OutputStream ostr)
    {
        ostr.writeInt(this.id);
        ostr.writeString(this.firstName);
        ostr.writeString(this.lastName);
        ostr.writeString(this.party);
    }

    public void ice_readMembers(com.zeroc.Ice.InputStream istr)
    {
        this.id = istr.readInt();
        this.firstName = istr.readString();
        this.lastName = istr.readString();
        this.party = istr.readString();
    }

    static public void ice_write(com.zeroc.Ice.OutputStream ostr, CandidateData v)
    {
        if(v == null)
        {
            _nullMarshalValue.ice_writeMembers(ostr);
        }
        else
        {
            v.ice_writeMembers(ostr);
        }
    }

    static public CandidateData ice_read(com.zeroc.Ice.InputStream istr)
    {
        CandidateData v = new CandidateData();
        v.ice_readMembers(istr);
        return v;
    }

    static public void ice_write(com.zeroc.Ice.OutputStream ostr, int tag, java.util.Optional<CandidateData> v)
    {
        if(v != null && v.isPresent())
        {
            ice_write(ostr, tag, v.get());
        }
    }

    static public void ice_write(com.zeroc.Ice.OutputStream ostr, int tag, CandidateData v)
    {
        if(ostr.writeOptional(tag, com.zeroc.Ice.OptionalFormat.FSize))
        {
            int pos = ostr.startSize();
            ice_write(ostr, v);
            ostr.endSize(pos);
        }
    }

    static public java.util.Optional<CandidateData> ice_read(com.zeroc.Ice.InputStream istr, int tag)
    {
        if(istr.readOptional(tag, com.zeroc.Ice.OptionalFormat.FSize))
        {
            istr.skip(4);
            return java.util.Optional.of(CandidateData.ice_read(istr));
        }
        else
        {
            return java.util.Optional.empty();
        }
    }

    private static final CandidateData _nullMarshalValue = new CandidateData();

    /** @hidden */
    public static final long serialVersionUID = -872166549L;
}
