package org.hibernate.bugs;

import org.hibernate.Hibernate;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

/**
 * This template demonstrates how to develop a test case for Hibernate ORM, using the Java Persistence API.
 */
public class JPAUnitTestCase {

	private EntityManagerFactory entityManagerFactory;

	@Before
	public void init() {
		entityManagerFactory = Persistence.createEntityManagerFactory("templatePU");
		createSmurfs();
	}

	@After
	public void destroy() {
		entityManagerFactory.close();
	}

	/**
	 * Simple Join does not fetch joined attribute.
	 * Children are complete.
	 * <p>
	 * JPQL.
	 */
	@Test
	public void simpleJoinDoesNotFetchAttributeJpql() {
		inTransaction(em -> {

			final TypedQuery<Parent> query = em.createQuery(
					"SELECT p FROM Parent p JOIN p.children c WHERE c.name = 'Baby Smurf'",
					Parent.class
			);
			final Parent parent = query.getSingleResult();

			assertThat(parent).isNotNull();
			assertThat(Hibernate.isInitialized(parent.children)).isFalse();
			assertThat(parent.children).hasSize(3);
		});

	}

	/**
	 * Simple Join does not fetch joined attribute.
	 * Children are complete.
	 * <p>
	 * Criteria API.
	 */
	@Test
	public void selectJoinWithSimpleConditionCriteriaApi() {
		inTransaction(em -> {

			final CriteriaBuilder cb = em.getCriteriaBuilder();
			final CriteriaQuery<Parent> query = cb.createQuery(Parent.class);
			final Root<Parent> root = query.from(Parent.class);
			final Join<Object, Object> join = root.join("children");
			query.where(cb.equal(join.get("name"), "Baby Smurf"));

			final Parent parent = em.createQuery(query).getSingleResult();

			assertThat(parent).isNotNull();
			assertThat(Hibernate.isInitialized(parent.children)).isFalse();
			assertThat(parent.children).hasSize(3);
		});

	}

	/**
	 * This not a fetch join as described in the specification.
	 * <p>
	 * It does limit the returned parent, but also limits the contained children.
	 * Since it is a fetch join the children list is initialized.
	 * <p>
	 * By this usage a FETCH JOIN is a JOIN.
	 */
	@Test
	public void fetchJoinUsedInCoditionLimitsResultsJpql() {

		inTransaction(em -> {

			final TypedQuery<Parent> query = em.createQuery(
					"SELECT p FROM Parent p JOIN FETCH p.children c WHERE c.name = 'Baby Smurf'",
					Parent.class
			);
			final Parent parent = query.getSingleResult();

			assertThat(parent).isNotNull();
			assertThat(Hibernate.isInitialized(parent.children)).isTrue();
			assertThat(parent.children).hasSize(1);
		});
	}

	/**
	 * One actually can use a fetch join as a join in the Criteria API if one casts the {@link javax.persistence.criteria.Fetch} to {@link Join}.
	 * <p>
	 * This is highly brittle since it depends on implementation details.
	 * <p>
	 * It does limit the returned parent, but also limits the contained children.
	 * Since it is a fetch join the children list is initialized.
	 */
	@Test
	public void fetchJoinUsedInCoditionHackedLimitsResultsCriteriaApi() {

		inTransaction(em -> {

			final CriteriaBuilder cb = em.getCriteriaBuilder();
			final CriteriaQuery<Parent> query = cb.createQuery(Parent.class);
			final Root<Parent> root = query.from(Parent.class);
			final Join<Object, Object> fetchJoin = (Join<Object, Object>) root.fetch("children"); // <--- evil hack because the implementation of fetch join happens to be a join.
			query.where(cb.equal(fetchJoin.get("name"), "Baby Smurf"));

			final Parent parent = em.createQuery(query).getSingleResult();


			assertThat(parent).isNotNull();
			assertThat(Hibernate.isInitialized(parent.children)).isTrue();
			assertThat(parent.children).hasSize(1);

			assertThat(root.getJoins()).hasSize(0); // <--- Look Ma, no joins
			assertThat(root.getFetches()).hasSize(1);
		});
	}

	/**
	 * One cannot use a path expression to silently reuse a fetch with Criteria API. The test fails when executed.
	 */
	@Test
	@Ignore
	public void fetchJoinUsedInCoditionFailsCriterApi() {

		inTransaction(em -> {

			final CriteriaBuilder cb = em.getCriteriaBuilder();
			final CriteriaQuery<Parent> query = cb.createQuery(Parent.class);
			final Root<Parent> root = query.from(Parent.class);
			root.fetch("children");
			query.where(cb.equal(root.get("children").get("name"), "Baby Smurf"));

			final Parent parent = em.createQuery(query).getSingleResult();

			assertThat(parent).isNotNull();
			assertThat(Hibernate.isInitialized(parent.children)).isFalse();
			assertThat(Hibernate.isInitialized(parent.favoriteChild)).isTrue();
			assertThat(parent.children).hasSize(3);
		});
	}

	/**
	 * This is what the specification suggests to use if one wants to use a joined attribute in a condition AND wants to fetch that join as well:
	 * Join fetch PLUS a JOIN initializes the collection and loads the children.
	 */
	@Test
	public void joinFetchPlusJoinWithConditionFetchesAllChildrenAndFiltersParentByChildrenJpql() {

		inTransaction(em -> {

			final TypedQuery<Parent> query = em.createQuery(
					"SELECT p FROM Parent p JOIN FETCH p.children JOIN p.children c WHERE c.name = 'Baby Smurf'",
					Parent.class
			);

			final Parent parent = query.getSingleResult();

			assertThat(parent).isNotNull();
			assertThat(Hibernate.isInitialized(parent.children)).isTrue();
			assertThat(parent.children).hasSize(3);
		});
	}

	/**
	 * This is what the specification suggests to use if one wants to use a joined attribute in a condition AND wants to fetch that join as well:
	 * Join fetch PLUS a JOIN initializes the collection and loads the children.
	 * <p>
	 * But when queried with {@code getResultList()} duplicates are returned.
	 */
	@Test
	public void joinFetchPlusJoinWithConditionFetchesAllChildrenAndFiltersParentByChildrenResultListJpql() {

		inTransaction(em -> {

			final TypedQuery<Parent> query = em.createQuery(
					"SELECT p FROM Parent p JOIN FETCH p.children JOIN p.children c WHERE c.name = 'Baby Smurf'",
					Parent.class
			);
			final List<Parent> parents = query.getResultList();

			assertThat(parents).hasSize(3);
			assertThat(parents.get(0)).isSameAs(parents.get(1)).isSameAs(parents.get(1));

			Parent parent = parents.get(0);

			assertThat(parent).isNotNull();
			assertThat(Hibernate.isInitialized(parent.children)).isTrue();
			assertThat(parent.children).hasSize(3);
		});
	}

	/**
	 * This is what the specification suggests to use if one wants to use a joined attribute in a condition AND wants to fetch that join as well:
	 * Join fetch PLUS a JOIN initializes the collection and loads the children.
	 */
	@Test
	public void joinFetchPlusJoinWithConditionFetchesAllChildrenAndFiltersParentByChildrenJpqlCriteriaApi() {

		inTransaction(em -> {

			final CriteriaBuilder cb = em.getCriteriaBuilder();
			final CriteriaQuery<Parent> query = cb.createQuery(Parent.class);
			final Root<Parent> root = query.from(Parent.class);
			root.fetch("children");
			final Join<Object, Object> join = root.join("children");
			query.where(cb.equal(join.get("name"), "Baby Smurf"));

			final Parent parent = em.createQuery(query).getSingleResult();

			assertThat(parent).isNotNull();
			assertThat(Hibernate.isInitialized(parent.children)).isTrue();
			assertThat(parent.children).hasSize(3);
		});
	}

	/**
	 * For * to One relations reusing a Fetch Join does not alter the result in a surprising way.
	 */
	@Test
	public void fetchJoinReusedByPathExpressionForOneToOneJpqlMatch() {

		inTransaction(em -> {

			final CriteriaBuilder cb = em.getCriteriaBuilder();
			final CriteriaQuery<Parent> query = cb.createQuery(Parent.class);
			final Root<Parent> root = query.from(Parent.class);
			root.fetch("favoriteChild");
			query.where(cb.equal(root.get("favoriteChild").get("name"), "Baby Smurf"));

			final Parent parent = em.createQuery(query).getSingleResult();

			assertThat(parent).isNotNull();
			assertThat(Hibernate.isInitialized(parent.children)).isFalse();
			assertThat(Hibernate.isInitialized(parent.favoriteChild)).isTrue();
			assertThat(parent.children).hasSize(3);
		});
	}


	/**
	 * For * to One relations reusing a Fetch Join does not alter the result in a surprising way.
	 */
	@Test
	public void fetchJoinReusedByPathExpressionForOneToOneJpqlNoMatch() {

		inTransaction(em -> {

			final CriteriaBuilder cb = em.getCriteriaBuilder();
			final CriteriaQuery<Parent> query = cb.createQuery(Parent.class);
			final Root<Parent> root = query.from(Parent.class);
			root.fetch("favoriteChild", JoinType.LEFT);
			query.where(cb.equal(root.get("favoriteChild").get("name"), "xxx"));

			final List<Parent> parents = em.createQuery(query).getResultList();

			assertThat(parents).isEmpty();
		});
	}

	private void createSmurfs() {

		inTransaction(em -> {

			final Parent p = new Parent();
			p.name = "Papa Smurf";
			p.children.add(new Child());
			p.children.get(0).name = "Baby Smurf";
			p.children.add(new Child());
			p.children.get(1).name = "Girl Smurf";
			p.children.add(new Child());
			p.children.get(2).name = "Boy Smurf";
			p.favoriteChild = p.children.get(0);

			em.persist(p);

			final Parent l = new Parent();
			l.name = "Lonely Smurf";
			em.persist(l);
		});
	}

	private <T> void inTransaction(Consumer<EntityManager> command) {

		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		try {
			command.accept(entityManager);
		} finally {
			entityManager.getTransaction().commit();
			entityManager.close();
		}

	}

	// select with fetch JPQL
	// select with join JPQL
	//
}
