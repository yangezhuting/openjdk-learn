package org.omg.CosNaming;


/**
* org/omg/CosNaming/Binding.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from /scratch/jenkins/workspace/8-2-build-olinux-amd64/jdk8u41/500/corba/src/share/classes/org/omg/CosNaming/nameservice.idl
* Tuesday, January 14, 2020 11:00:47 PM PST
*/

public final class Binding implements org.omg.CORBA.portable.IDLEntity
{
  public org.omg.CosNaming.NameComponent binding_name[] = null;

  // name
  public org.omg.CosNaming.BindingType binding_type = null;

  public Binding ()
  {
  } // ctor

  public Binding (org.omg.CosNaming.NameComponent[] _binding_name, org.omg.CosNaming.BindingType _binding_type)
  {
    binding_name = _binding_name;
    binding_type = _binding_type;
  } // ctor

} // class Binding