package org.hibernate.bugs;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
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
	 * A condition on a join limits the resulting parent entity, but not their referenced entities
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
			assertThat(parent.children).hasSize(3);
			return  null;
		});

	}

	/**
	 * This not a fetch join as described in the specification.
	 *
	 * It does limit the returned parent, but also limits the contained children.
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
			assertThat(parent.children).hasSize(1);
			return  null;
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
