/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 1.3.35
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package sml;

import java.util.List;

import org.jsoar.kernel.memory.Wme;
import org.jsoar.kernel.memory.Wmes;

import com.google.common.collect.Iterators;


public class Identifier extends WMElement {

    // Two identifiers (i.e. two wmes) can share the same identifier value
    // So each identifier has a pointer to a symbol object, but two could share the same object.
    final IdentifierSymbol m_pSymbol ;

    // This version is only needed at the top of the tree (e.g. the input link)
    Identifier(Agent pAgent, IdentifierSymbol valueId)
    {
        super(pAgent, null, null);
        
        m_pSymbol = valueId;
        //m_pSymbol.SetIdentifierSymbol(pIdentifier);
        //RecordSymbolInMap();
    }

    // The normal case (where there is a parent id)
    Identifier(Agent pAgent, IdentifierSymbol pParent, Wme wme)
    {
        super(pAgent, pParent, wme);
        
        m_pSymbol = new IdentifierSymbol(wme.getValue().asIdentifier());
        //m_pSymbol.SetIdentifierSymbol(pIdentifier);
        //RecordSymbolInMap();
    }
    /*
    Identifier(Agent pAgent, IdentifierSymbol pParentSymbol, String pID, String pAttributeName, String pIdentifier, int timeTag)
    {
        super(pAgent, pParentSymbol, pID, pAttributeName, timeTag);
        
        m_pSymbol = new IdentifierSymbol(this);
        m_pSymbol.SetIdentifierSymbol(pIdentifier);
        RecordSymbolInMap();
    }

    // The shared id case (where there is a parent id and value is an identifier that already exists)
    Identifier(Agent pAgent, Identifier pParent, String pID, String pAttributeName, Identifier pLinkedIdentifier, int timeTag) 
    {
        super(pAgent, pParent.GetSymbol(), pID, pAttributeName, timeTag);
        
        m_pSymbol = pLinkedIdentifier.m_pSymbol;
        m_pSymbol.UsedBy(this);
        RecordSymbolInMap();
    }
    Identifier(Agent pAgent, IdentifierSymbol pParentSymbol, String pID, String pAttributeName, IdentifierSymbol pLinkedIdentifierSymbol, int timeTag)
    {
        super(pAgent, pParentSymbol, pID, pAttributeName, timeTag);
        
        m_pSymbol = pLinkedIdentifierSymbol;
        m_pSymbol.UsedBy(this);
        RecordSymbolInMap();
    }
    


    void AddChild(WMElement pWME) { m_pSymbol.AddChild(pWME) ; }

    void RemoveChild(WMElement pWME) { m_pSymbol.RemoveChild(pWME) ; }

//#ifdef SML_DIRECT
//    virtual void DirectAdd(Direct_AgentSML_Handle pAgentSML, long timeTag) ;
//#endif

    // Send over to the kernel again
    void Refresh()
    {
        if(GetAgent().GetInputLink() != this)
        {
            super.Refresh();
        }
        
        if(m_pSymbol.IsFirstUser(this))
        {
            for(WMElement pWME : m_pSymbol.m_Children)
            {
                pWME.Refresh();
            }
        }
    }
    
    */
    
    IdentifierSymbol GetSymbol() { return m_pSymbol ; }

    /*
    void SetParent(Identifier pParent)
    {
        super.SetParent(pParent);
    }

    void RecordSymbolInMap()
    {
        GetAgent().GetWM().RecordSymbolInMap(m_pSymbol);
    }

*/
    //virtual ~Identifier(void);
  public synchronized void delete() {
      /*
      m_pSymbol.NoLongerUsedBy(this);
      if(m_pSymbol.GetNumberUsing() == 0)
      {
          GetAgent().GetWM().RemoveSymbolFromMap(m_pSymbol);
          m_pSymbol.delete();
      }
      //m_pSymbol = null;
    super.delete();
    */
  }

  public String GetValueType() {
      return sml_Names.getKTypeID();
  }

  public String GetValueAsString() {
      return m_pSymbol.GetIdentifierSymbol();
  }

  public boolean IsIdentifier() {
      return true;
  }

  public Identifier ConvertToIdentifier() {
      return this;
  }

  public WMElement FindFromTimeTag(int timeTag) {
      return m_Agent.GetWM().timetagMap.get(timeTag);
  }

  public WMElement FindByAttribute(String pAttribute, int index) {
      List<Wme> kids = Wmes.matcher(m_Agent.agent).attr(pAttribute).filter(m_pSymbol.id);
      return index < kids.size() ? m_Agent.GetWM().findWme(kids.get(index)) : null;
  }

  public String GetParameterValue(String pAttribute) {
    WMElement pWME = FindByAttribute(pAttribute, 0); 
    return pWME != null ? pWME.GetValueAsString() : null ;
  }

  public String GetCommandName() {
      return GetAttribute();
  }

  public void AddStatusComplete() {
      GetAgent().CreateStringWME(this, "status", "complete");
  }

  public void AddStatusError() {
      GetAgent().CreateStringWME(this, "status", "error");
  }

  public void AddErrorCode(int errorCode) {
      GetAgent().CreateIntWME(this, "error-code", errorCode);
  }

  public int GetNumberChildren() {
      return Iterators.size(m_pSymbol.id.getWmes()); // TODO slow
  }

  public WMElement GetChild(int index) {
      return GetAgent().GetWM().findWme(Iterators.get(m_pSymbol.id.getWmes(), index));
  }

  public boolean AreChildrenModified() {
      return m_pSymbol.AreChildrenModified();
  }

}
