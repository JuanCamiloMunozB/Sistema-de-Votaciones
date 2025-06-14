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

public class VotingTableData implements java.lang.Cloneable,
                                        java.io.Serializable
{
    public int id;

    public CitizenData[] citizens;

    public VotingTableData()
    {
    }

    public VotingTableData(int id, CitizenData[] citizens)
    {
        this.id = id;
        this.citizens = citizens;
    }

    public boolean equals(java.lang.Object rhs)
    {
        if(this == rhs)
        {
            return true;
        }
        VotingTableData r = null;
        if(rhs instanceof VotingTableData)
        {
            r = (VotingTableData)rhs;
        }

        if(r != null)
        {
            if(this.id != r.id)
            {
                return false;
            }
            if(!java.util.Arrays.equals(this.citizens, r.citizens))
            {
                return false;
            }

            return true;
        }

        return false;
    }

    public int hashCode()
    {
        int h_ = 5381;
        h_ = com.zeroc.IceInternal.HashUtil.hashAdd(h_, "::ElectionSystem::VotingTableData");
        h_ = com.zeroc.IceInternal.HashUtil.hashAdd(h_, id);
        h_ = com.zeroc.IceInternal.HashUtil.hashAdd(h_, citizens);
        return h_;
    }

    public VotingTableData clone()
    {
        VotingTableData c = null;
        try
        {
            c = (VotingTableData)super.clone();
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
        CitizenDataSeqHelper.write(ostr, this.citizens);
    }

    public void ice_readMembers(com.zeroc.Ice.InputStream istr)
    {
        this.id = istr.readInt();
        this.citizens = CitizenDataSeqHelper.read(istr);
    }

    static public void ice_write(com.zeroc.Ice.OutputStream ostr, VotingTableData v)
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

    static public VotingTableData ice_read(com.zeroc.Ice.InputStream istr)
    {
        VotingTableData v = new VotingTableData();
        v.ice_readMembers(istr);
        return v;
    }

    static public void ice_write(com.zeroc.Ice.OutputStream ostr, int tag, java.util.Optional<VotingTableData> v)
    {
        if(v != null && v.isPresent())
        {
            ice_write(ostr, tag, v.get());
        }
    }

    static public void ice_write(com.zeroc.Ice.OutputStream ostr, int tag, VotingTableData v)
    {
        if(ostr.writeOptional(tag, com.zeroc.Ice.OptionalFormat.FSize))
        {
            int pos = ostr.startSize();
            ice_write(ostr, v);
            ostr.endSize(pos);
        }
    }

    static public java.util.Optional<VotingTableData> ice_read(com.zeroc.Ice.InputStream istr, int tag)
    {
        if(istr.readOptional(tag, com.zeroc.Ice.OptionalFormat.FSize))
        {
            istr.skip(4);
            return java.util.Optional.of(VotingTableData.ice_read(istr));
        }
        else
        {
            return java.util.Optional.empty();
        }
    }

    private static final VotingTableData _nullMarshalValue = new VotingTableData();

    /** @hidden */
    public static final long serialVersionUID = -1811358618L;
}
