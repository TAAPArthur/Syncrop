Syncrop
=========
ROP Syncronization Service
--------------------
What is Syncrop?

Syncrop is a free, open source cloud-based syncronization service. The entire project is written in Java 7.

Main Features: 
- *File syncing* Whenever a file modifed on your local machine it syncs to the cloud which them syncs it with any other machine authorized with your account.
- *Offline managament* Whenever a local file is modified while you are not connected to the internet, it will be immidetaily be synced upon reconnection
- *Conflict handinling* If 2 files were modifed on 2 diffrent offline machines, upon both connecting to the Internet, the newer one is kept and the elder is archived.
- *Cross platform* Supports Linux and Windows (7,8,10)

Layout:
- Under Linux/syncrop is the psudeo-root tree used to generate the latest deb file
- All the source files are under Syncrop

Installing
- A [syncrop.deb](https://github.com/TAAPArthur/Syncrop/releases/latest) is available for download
- To download all the Jars, go [here](https://github.com/TAAPArthur/Syncrop/tree/master/Linux/syncrop/usr/lib/syncrop)
