package org.omg.CosNaming.NamingContextExtPackage;


/**
* org/omg/CosNaming/NamingContextExtPackage/InvalidAddress.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from /scratch/jenkins/workspace/8-2-build-olinux-amd64/jdk8u41/500/corba/src/share/classes/org/omg/CosNaming/nameservice.idl
* Tuesday, January 14, 2020 11:00:47 PM PST
*/

public final class InvalidAddress extends org.omg.CORBA.UserException
{

  public InvalidAddress ()
  {
    super(InvalidAddressHelper.id());
  } // ctor


  public InvalidAddress (String $reason)
  {
    super(InvalidAddressHelper.id() + "  " + $reason);
  } // ctor

} // class InvalidAddress