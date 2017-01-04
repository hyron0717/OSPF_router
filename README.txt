My assignement is implemented in Java.

How to run:
1.Type 'make' to compile router.java

2.Run nse-linux386 in one machine

On hostX: ./nse-linux 386 <hostY> <portA>
e.g.  
On ubuntu1404-002: ./nse-linux386 ubuntu1404-004 2000

3.Run router 1 to 5 in order in another machine

On hostY: java router <router_id> <hostX> <portA> <portB>
e.g.
On ubuntu1404-004: java router 1 ubuntu1404-002 2000 2001
		   java router 2 ubuntu1404-002 2000 2002
		   java router 3 ubuntu1404-002 2000 2003
		   java router 4 ubuntu1404-002 2000 2004
		   java router 5 ubuntu1404-002 2000 2005

4.The program will create router1.log to router5.log, they show how the routers send hello or LSPDU packet to each other and how to get the routing information base(RIB)

5.Type 'make clean' to delete all the class and log


-------------------------------------------------------------------------

Version:
javac -version: javac 1.8.0_91
make -v: GNU Make 3.81
hostname -f: ubuntu1404-002.student.cs.uwaterloo.ca
             ubuntu1404-004.student.cs.uwaterloo.ca

