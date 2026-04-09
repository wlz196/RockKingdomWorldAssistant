package com.roco.backend;

import com.roco.backend.model.entity.Pet;
import com.roco.backend.repository.PetRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
public class PetRepositoryTest {

    @Autowired
    private PetRepository petRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private ChatLanguageModel chatLanguageModel;

    @Test
    public void testFindPetByName() {
        // 尝试从我们的 data.db 中查询一个已知的精灵
        Optional<Pet> pet = petRepository.findByName("迪莫");
        
        if (pet.isPresent()) {
            assertThat(pet.get().getName()).isEqualTo("迪莫");
            System.out.println("✅ 数据库查询成功！");
            System.out.println("精灵名称: " + pet.get().getName());
            System.out.println("精力种族值: " + pet.get().getHp());
        } else {
            System.out.println("❌ 数据库中未找到 '迪莫'，请检查 data.db 是否在 backend 目录的上级文件夹中。");
            assertThat(pet).isPresent();
        }
    }
}
