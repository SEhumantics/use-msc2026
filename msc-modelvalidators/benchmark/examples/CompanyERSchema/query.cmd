mv ? enable
mv -validate CompanyER.properties

mv ? Employee.allInstances()->size()
mv ? Department.allInstances()->forAll(d | Employee.allInstances()->exists(e | e.dname = d.dname))

quit
