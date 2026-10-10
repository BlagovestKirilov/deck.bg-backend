package bg.deck.santase.util;

import bg.deck.santase.model.Card;
import bg.deck.santase.model.dto.CardDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {EnumMapper.class})
public interface CardMapper {

    @Mapping(source = "suit", target = "suit")
    @Mapping(source = "rank", target = "rank")
    CardDTO toDTO(Card card);

    List<CardDTO> toDTO(List<Card> cards);
}
