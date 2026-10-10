package bg.deck.santase.util;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EnumMapper {

    default String map(Enum<?> anEnum) {
        if (anEnum == null) {
            return null;
        }
        return anEnum.name();
    }
}
