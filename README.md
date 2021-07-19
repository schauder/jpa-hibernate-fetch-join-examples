# Examples for how Fetch Joins work with Hibernate.

and their effects on the select result.

* Turning a Join into a Fetch Join might multiply the number of parent entities by their number of children. This seems to actually be covered by the specification:
    > A fetch join has the same join semantics as the corresponding inner or outer join, except that the related objects specified on the right-hand side of the join operation are not returned in the query result or otherwise referenced in the query. Hence, for example, if department 1 has five employees, the above query *returns five references to the department 1 entity*.
    _Section 4.4.5.3 of the JPA Specification_

* `getSingleResult` succeeds if a single entity is returned multiple times due to a Fetch Join. This seems to be in violation of the Specification which in the JavaDoc of `getSingleResult` says:
    > @throws NonUniqueResultException if more than one result
* A fetch join might implicitely be referenced by a path expression in the WHERE-clause in JPQL, but not in the Criteria API.
    If done so, the number of children returned is limited by the condition. The behaviour is undefined by the Specification but the possibility to do so seems in contradiction to the specification which explicitely mentions
    > A fetch join has the same join semantics as the corresponding inner or outer join, except that *the related objects specified on the right-hand side of the join operation are not* returned in the query result or *otherwise referenced in the query*.
    _Section 4.4.5.3 of the JPA Specification_
* A fetch join actually can be reused in the Criteria API by Hibernate once one realises that a `Fetch` implementation of Hibernate may be cast to a `Join` and then used as such.

All the tests are in the single test class:
https://github.com/schauder/jpa-hibernate-fetch-join-examples/blob/main/src/test/java/org/hibernate/bugs/JPAUnitTestCase.java
