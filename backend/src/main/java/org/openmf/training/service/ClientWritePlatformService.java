package org.openmf.training.service;

import java.util.List;
import org.openmf.training.data.ClientData;
import org.openmf.training.data.CreateClientRequest;
import org.openmf.training.data.UpdateClientRequest;
import org.openmf.training.domain.Client;
import org.openmf.training.domain.ClientStatus;
import org.openmf.training.exception.PlatformApiDataValidationException;
import org.openmf.training.exception.ResourceNotFoundException;
import org.openmf.training.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sample write/read service for clients — same layering idea as Fineract platform services.
 */
@Service
public class ClientWritePlatformService {

    private static final Logger log = LoggerFactory.getLogger(ClientWritePlatformService.class);

    private final ClientRepository clientRepository;

    public ClientWritePlatformService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientData> retrieveAll() {
        List<ClientData> clients = clientRepository.findAll().stream().map(this::toData).toList();
        log.debug("retrieveAll clients count={}", clients.size());
        return clients;
    }

    @Transactional(readOnly = true)
    public ClientData retrieveOne(Long id) {
        log.debug("retrieveOne client id={}", id);
        return toData(findClient(id));
    }

    @Transactional
    public ClientData create(CreateClientRequest request) {
        if (request == null || isBlank(request.firstName()) || isBlank(request.lastName())) {
            throw new PlatformApiDataValidationException("firstName and lastName are required");
        }

        Client client = new Client();
        applyFields(
                client,
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phoneNumber(),
                request.address(),
                request.city(),
                request.state(),
                request.zipCode(),
                request.country(),
                request.dateOfBirth(),
                request.gender());
        client.setStatus(ClientStatus.ACTIVE);
        ClientData saved = toData(clientRepository.save(client));
        log.info("Created client id={} name={} {}", saved.id(), saved.firstName(), saved.lastName());
        return saved;
    }

    @Transactional
    public ClientData update(Long id, UpdateClientRequest request) {
        if (request == null || isBlank(request.firstName()) || isBlank(request.lastName())) {
            throw new PlatformApiDataValidationException("firstName and lastName are required");
        }
        Client client = findClient(id);
        applyFields(
                client,
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phoneNumber(),
                request.address(),
                request.city(),
                request.state(),
                request.zipCode(),
                request.country(),
                request.dateOfBirth(),
                request.gender());
        ClientData saved = toData(clientRepository.save(client));
        log.info("Updated client id={}", saved.id());
        return saved;
    }

    /**
     * Command-style sample (like Fineract {@code ?command=}).
     */
    @Transactional
    public ClientData handleCommand(Long id, String command) {
        if (command == null || command.isBlank()) {
            throw new PlatformApiDataValidationException("Query param 'command' is required");
        }
        Client client = findClient(id);
        return switch (command.trim().toLowerCase()) {
            case "close" -> {
                client.setStatus(ClientStatus.CLOSED);
                ClientData saved = toData(clientRepository.save(client));
                log.info("Closed client id={}", id);
                yield saved;
            }
            case "activate" -> {
                client.setStatus(ClientStatus.ACTIVE);
                ClientData saved = toData(clientRepository.save(client));
                log.info("Activated client id={}", id);
                yield saved;
            }
            default -> throw new PlatformApiDataValidationException(
                    "Unknown command: " + command + ". Use close|activate");
        };
    }

    private void applyFields(
            Client client,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            String address,
            String city,
            String state,
            String zipCode,
            String country,
            java.time.LocalDate dateOfBirth,
            String gender) {
        client.setFirstName(firstName.trim());
        client.setLastName(lastName.trim());
        client.setEmail(trimToNull(email));
        client.setPhoneNumber(trimToNull(phoneNumber));
        client.setAddress(trimToNull(address));
        client.setCity(trimToNull(city));
        client.setState(trimToNull(state));
        client.setZipCode(trimToNull(zipCode));
        client.setCountry(trimToNull(country));
        client.setDateOfBirth(dateOfBirth);
        client.setGender(trimToNull(gender));
    }

    private Client findClient(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + id));
    }

    private ClientData toData(Client client) {
        return new ClientData(
                client.getId(),
                client.getFirstName(),
                client.getLastName(),
                client.getEmail(),
                client.getPhoneNumber(),
                client.getAddress(),
                client.getCity(),
                client.getState(),
                client.getZipCode(),
                client.getCountry(),
                client.getDateOfBirth(),
                client.getGender(),
                client.getStatus(),
                client.getCreatedOn());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
