package com.cobre.notifications.infrastructure.persistence;

import com.cobre.notifications.domain.model.Client;
import com.cobre.notifications.domain.port.out.ClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class ClientJpaRepositoryTest extends IntegrationTestBase {

    @Autowired
    private ClientRepository clientRepository;

    @Test
    void persistAndFindByUniqueCode() {
        Client client = new Client(null, null, "Alan Gonzalez", "alan@cobre.com", null, null, null);
        Client saved = clientRepository.save(client);
        entityManager.flush();
        entityManager.clear();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.uniqueCode()).isNotNull();
        assertThat(saved.createdDate()).isNotNull();
        assertThat(saved.lastModifiedDate()).isNotNull();

        Optional<Client> found = clientRepository.findByUniqueCode(saved.uniqueCode());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Alan Gonzalez");
        assertThat(found.get().email()).isEqualTo("alan@cobre.com");
        assertThat(found.get().deleted()).isFalse();
    }

    @Test
    void softDelete_excludedFromActiveQueries() {
        Client client = new Client(null, null, "Soft Delete Client", "softdelete@cobre.com", null, null, null);
        Client saved = clientRepository.save(client);
        entityManager.flush();
        entityManager.clear();

        clientRepository.softDelete(saved.uniqueCode());
        entityManager.flush();
        entityManager.clear();

        List<Client> active = clientRepository.findAllActive();
        assertThat(active).extracting(Client::uniqueCode).doesNotContain(saved.uniqueCode());

        Optional<Client> found = clientRepository.findByUniqueCode(saved.uniqueCode());
        assertThat(found).isEmpty();
    }

    @Test
    void duplicateEmail_throwsConstraintViolation() {
        Client client1 = new Client(null, null, "Client One", "duplicate@cobre.com", null, null, null);
        clientRepository.save(client1);
        entityManager.flush();

        Client client2 = new Client(null, null, "Client Two", "duplicate@cobre.com", null, null, null);

        assertThatThrownBy(() -> {
            clientRepository.save(client2);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
