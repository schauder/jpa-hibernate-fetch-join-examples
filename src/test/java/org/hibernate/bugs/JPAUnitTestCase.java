package org.hibernate.bugs;

import org.assertj.core.api.Assertions;
import org.hibernate.Hibernate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Fetch;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

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
	 * A condition on a join limits the resulting parent entity, but not their referenced entities.
	 * Since the children relation is lazy and no fetch join was specified the children property is not initialized.
	 */
	@Test
	public void selectJoinWithSimpleCondition() {
		inTransaction(em -> {

			final TypedQuery<Parent> query = em.createQuery(
					"SELECT p FROM Parent p JOIN p.children c WHERE c.name = 'Baby Smurf'",
					Parent.class
			);
			final Parent parent = query.getSingleResult();

			assertThat(parent).isNotNull();
			assertThat(Hibernate.isInitialized(parent.children)).isFalse();
			assertThat(parent.children).hasSize(3);
			return  null;
		});

	}

	/**
	 * A condition on a join limits the resulting parent entity, but not their referenced entities.
	 * Since the children relation is lazy and no fetch join was specified the children property is not initialized.
	 */
	@Test
	public void selectJoinWithSimpleConditionCriteria() {
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
			return  null;
		});

	}

	/**
	 * This not a fetch join as described in the specification.
	 *
	 * It does limit the returned parent, but also limits the contained children.
	 * Since it is a fetch join the children list is initialized.
	 */
	@Test
	public void selectJoinFetchWithSimpleCondition() {

		inTransaction(em -> {

			final TypedQuery<Parent> query = em.createQuery(
					"SELECT p FROM Parent p JOIN FETCH p.children c WHERE c.name = 'Baby Smurf'",
					Parent.class
			);
			final Parent parent = query.getSingleResult();

			assertThat(parent).isNotNull();
			assertThat(Hibernate.isInitialized(parent.children)).isTrue();
			assertThat(parent.children).hasSize(1);
			return  null;
		});
	}

	/**
	 * This not a fetch join as described in the specification.
	 *
	 * And it is not expressable in Criteria API
	 */
	@Test
	public void selectJoinFetchWithSimpleConditionCriteria() {

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
			return  null;
		});
	}

	/**
	 * Join fetch PLUS a JOIN initializes the collection and loads the children.
	 */
	@Test
	public void selectJoinFetchAndJoinWithSimpleConditionGet() {

		inTransaction(em -> {

			final TypedQuery<Parent> query = em.createQuery(
					"SELECT p FROM Parent p JOIN FETCH p.children JOIN p.children c WHERE c.name = 'Baby Smurf'",
					Parent.class
			);

			final Parent parent = query.getSingleResult();

			assertThat(parent).isNotNull();
			assertThat(Hibernate.isInitialized(parent.children)).isTrue();
			assertThat(parent.children).hasSize(3);
			return null;
		});
	}
	/**
	 * Join fetch PLUS a JOIN initializes the collection and loads the children.
	 */
	@Test
	public void selectJoinFetchAndJoinWithSimpleConditionResultList() {

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
			return null;
		});
	}

	/**
	 * Join fetch PLUS a JOIN initializes the collection and loads the children.
	 */
	@Test
	public void selectJoinFetchAndJoinWithSimpleConditionCriteria() {

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
			return null;
		});
	}

	/**
	 * Join fetch PLUS a JOIN initializes the collection and loads the children.
	 */
	@Test
	public void selectReusingFetchWithPathExpressionWithSimpleConditionCriteria() {

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
			return null;
		});
	}

	/**
	 * Join fetch PLUS a JOIN initializes the collection and loads the children.
	 */
	@Test
	public void selectReusingLeftOuterFetchWithPathExpressionWithSimpleConditionCriteria() {

		inTransaction(em -> {

			final CriteriaBuilder cb = em.getCriteriaBuilder();
			final CriteriaQuery<Parent> query = cb.createQuery(Parent.class);
			final Root<Parent> root = query.from(Parent.class);
			root.fetch("favoriteChild", JoinType.LEFT);
			query.where(cb.equal(root.get("favoriteChild").get("name"), "xxx"));

			final List<Parent> parents = em.createQuery(query).getResultList();

			assertThat(parents).isEmpty();
			return null;
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

			return p;
		});
	}

	private <T> T inTransaction(Function<EntityManager, T> command) {
		EntityManager entityManager = entityManagerFactory.createEntityManager();
		entityManager.getTransaction().begin();

		T result = command.apply(entityManager);

		entityManager.getTransaction().commit();
		entityManager.close();

		return result;
	}

	// select with fetch JPQL
	// select with join JPQL
	//
}
