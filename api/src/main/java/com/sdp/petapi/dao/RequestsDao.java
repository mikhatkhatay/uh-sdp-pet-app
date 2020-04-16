package com.sdp.petapi.dao;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sdp.petapi.models.*;
import com.sdp.petapi.repositories.RequestsRepository;

@Component
public class RequestsDao {

  @Autowired
  transient RequestsRepository repository;

  @Autowired
  transient UserDao userDao;

  @Autowired
  transient PetDao petDao;

  
  private static final String CANCELED_STRING = "CANCELED";
  private static final String APPROVED_STRING = "APPROVED";
  private static final String PENDING_STRING  = "PENDING";

  public List<Requests> getAllRequests() {
    return repository.findAll();
  }

  public Requests getRequestById(String reqid){
    if (reqid == null) return null;
    Optional<Requests> req = repository.findById(reqid);
    
    return req.isPresent() ? req.get() : null;
  }

  public Requests createRequest(String userid, String petid) {
    if(!isRequestValid(userid, petid)) return null;

    Pet pet = petDao.getPetById(petid);

    /* Conrad: right now make it so request makes status "PENDING" (happens in constructor) 
       and pet.isAdopted = T and pet.isActive = F */
    pet.setActive(false);
    pet.setAdopted(true);
    petDao.putPetByRequest(pet);
    return repository.insert(new Requests(userid, petid));
  }

  private Boolean isRequestValid(String userid, String petid) {
    if(!areComponentsValid(userid, petid)) return false;

    if (isRequestDuplicate(userid, petid)) return false;
    return true;
  }

  private Boolean areComponentsValid(String userid, String petid) {
    User user = userDao.getUserById(userid);
    if (!isUserValid(user)) return false;

    Pet pet = petDao.getPetById(petid);
    if (!isPetValid(pet)) return false;
    return true;
  }

  private Boolean isUserValid(User user) {
    return user != null && !user.isEmployee();
  }

  private Boolean isPetValid(Pet pet) {
    return pet != null && pet.isActive();
  }

  private Boolean isRequestDuplicate(String userid, String petid) {
    return getAllRequests()
      .stream()
      .anyMatch(r -> r.getPetid().equals(petid) && r.getUserid().equals(userid)
        && !r.getStatus().equals(CANCELED_STRING));
  }

  public Requests putRequests(String reqid, String status) {
    return (status.equals(APPROVED_STRING)) ? approveRequest(reqid) : 
      (status.equals(CANCELED_STRING)) ? cancelRequest(reqid) : null;
  }

  public Requests approveRequest(String reqid) {
    Requests req = getRequestById(reqid);

    List<Requests> cancelReqs = repository.findAll().stream()
      .filter(r -> !r.getUserid().equals(req.getUserid())
        && r.getPetid().equals(req.getPetid())
        && r.getStatus().equals(PENDING_STRING))
      .collect(Collectors.toList());
    
    if (!cancelReqs.isEmpty()) {
      cancelReqs.forEach(r -> r.setStatus(CANCELED_STRING));
      repository.saveAll(cancelReqs);
    }
    
    req.setStatus(APPROVED_STRING);
    return repository.save(req);
  }

  public Requests cancelRequest(String reqid) {
    Requests req = getRequestById(reqid);

    if (!repository.findAll()
      .stream()
      .anyMatch(
        r -> r.getPetid().equals(req.getPetid())
        && !r.getUserid().equals(req.getUserid())
        && !r.getStatus().equals(CANCELED_STRING)
      )
    ) {
      /* To undo Conrad's earlier comment */
      Pet pet = petDao.getPetById(req.getPetid());
      pet.setActive(true);
      pet.setAdopted(false);
      petDao.putPetByRequest(pet);
    }
    req.setStatus(APPROVED_STRING);
    return repository.save(req);
  }

  public Requests deleteRequest(String reqid) {
    Requests req = getRequestById(reqid);
    if (req == null) return null;

    repository.delete(req);
    return req;
  }

}