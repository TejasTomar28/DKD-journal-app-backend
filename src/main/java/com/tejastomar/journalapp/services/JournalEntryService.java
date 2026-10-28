package com.tejastomar.journalapp.services;

import lombok.extern.slf4j.Slf4j;
import com.tejastomar.journalapp.entity.JournalEntry;
import com.tejastomar.journalapp.entity.User;
import com.tejastomar.journalapp.repository.JournalEntryRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class JournalEntryService  {

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private UserService userService;

    @Transactional
    public JournalEntry saveEntry(JournalEntry journalEntry, String userName) {
        try{
            User user = userService.findByUserName(userName);
            journalEntry.setDate(LocalDateTime.now());
            JournalEntry saved = journalEntryRepository.save(journalEntry);
            user.getJournalEntries().add(saved);
            userService.saveUser(user);
            return saved;
        }catch(Exception e){
            System.out.println(e);
            throw new RuntimeException("An error while saving the entry!",e);
        }
    }

    public JournalEntry saveEntry(JournalEntry journalEntry){
        return journalEntryRepository.save(journalEntry);
    }

    public List<JournalEntry> getAll(){
        return journalEntryRepository.findAll();
    }

    public Optional<JournalEntry> findById(ObjectId myId){
        return journalEntryRepository.findById(myId);
    }

    @Transactional
    public boolean deleteById(ObjectId myId, String username){
        boolean removed=false;
        try{
            User user=userService.findByUserName(username);
            removed=user.getJournalEntries().removeIf(entry->entry.getId().equals(myId));
            if(removed){
                userService.saveUser(user);
                journalEntryRepository.deleteById(myId);
            }
            return removed;
        }catch(Exception e){
            log.error("error: ",e);
            throw new RuntimeException("An error while deleting the entry!",e);
        }
    }

    public List<JournalEntry> findByUserName(String username){
        return null;
    }
}

//controller--->service--->repository
